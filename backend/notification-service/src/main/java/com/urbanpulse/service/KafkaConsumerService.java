package com.urbanpulse.service;

import com.urbanpulse.model.Alert;
import com.urbanpulse.model.CityMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumes processed analytics from Kafka, stores latest metrics, and broadcasts updates.
 *
 * Alert intelligence behavior:
 * - Dedupes repeated conditions for the same city.
 * - Applies cooldown for repeated non-escalating alerts.
 * - Emits escalation alerts when risk worsens.
 * - Emits recovery alerts when city returns to NORMAL.
 */
@Slf4j
@Service
public class KafkaConsumerService {

    private static final long ALERT_COOLDOWN_MS = 10 * 60 * 1000L;

    @Autowired
    private MetricsStoreService metricsStoreService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();
    private final Map<String, String> lastSeenRiskLevel = new ConcurrentHashMap<>();
    private final Map<String, String> lastAlertFingerprint = new ConcurrentHashMap<>();

    @KafkaListener(topics = "${spring.kafka.topic.processed-city-data}",
                   groupId = "${spring.kafka.consumer.group-id}")
    @SuppressWarnings("null")
    public void consumeProcessedData(CityMetrics metrics) {
        try {
            if (metrics == null || metrics.getCity() == null) {
                log.warn("Received invalid metrics payload from Kafka");
                return;
            }

            String cityKey = metrics.getCity().toLowerCase();
            String currentRisk = metrics.getRiskLevel() != null ? metrics.getRiskLevel() : "NORMAL";
            String previousRisk = lastSeenRiskLevel.getOrDefault(cityKey, "NORMAL");
            long now = System.currentTimeMillis();

            metricsStoreService.storeMetrics(metrics);
            metricsStoreService.addAqiHistory(metrics.getCity(), metrics.getTimestamp(), metrics.getAqi());

            AlertDecision decision = evaluateAlertDecision(metrics, cityKey, currentRisk, previousRisk, now);

            // Always track latest risk state, even if no alert was emitted.
            lastSeenRiskLevel.put(cityKey, currentRisk);

            if (decision.shouldAlert()) {
                Alert alert = Alert.builder()
                        .id(UUID.randomUUID().toString())
                        .city(metrics.getCity())
                        .timestamp(metrics.getTimestamp())
                        .riskLevel(currentRisk)
                        .message(buildFinalMessage(metrics, decision.alertType()))
                        .aqi(metrics.getAqi())
                        .cityHealthScore(metrics.getCityHealthScore())
                        .primaryDriver(metrics.getPrimaryDriver())
                        .alertType(decision.alertType())
                        .alertState(mapAlertState(decision.alertType()))
                        .build();

                alertService.addAlert(alert);

                messagingTemplate.convertAndSend(
                        "/topic/city-updates",
                        Map.of(
                                "type", "alert",
                                "city", metrics.getCity(),
                                "data", alert,
                                "timestamp", now
                        )
                );

                log.info("Alert [{}] for {}: {} -> {}", decision.alertType(), metrics.getCity(), previousRisk, currentRisk);
            }

            messagingTemplate.convertAndSend(
                    "/topic/city-updates",
                    Map.of(
                            "type", "update",
                            "city", metrics.getCity(),
                            "data", metrics,
                            "timestamp", now
                    )
            );

        } catch (Exception e) {
            log.error("Error processing metric from Kafka", e);
        }
    }

    private AlertDecision evaluateAlertDecision(CityMetrics metrics,
                                                String cityKey,
                                                String currentRisk,
                                                String previousRisk,
                                                long now) {
        // Spec alert conditions
        int aqi = metrics.getAqi() != null ? metrics.getAqi() : 0;
        double deviationPct = metrics.getAqiDeviationPercent() != null ? metrics.getAqiDeviationPercent() : 0.0;
        boolean isAnomaly = Boolean.TRUE.equals(metrics.getAnomaly());

        boolean isSevere = aqi > 200;
        boolean isHighRisk = aqi > 150 && deviationPct > 20;
        boolean previouslyHighOrAbove = riskRank(previousRisk) >= 3; // HIGH_RISK or SEVERE
        boolean recovered = aqi < 120 && previouslyHighOrAbove;

        String fingerprint = createFingerprint(metrics);
        String lastFingerprint = lastAlertFingerprint.get(cityKey);
        boolean isDuplicateCondition = Objects.equals(fingerprint, lastFingerprint);

        long lastAlertAt = lastAlertTime.getOrDefault(cityKey, 0L);
        boolean cooldownExpired = (now - lastAlertAt) >= ALERT_COOLDOWN_MS;

        if (recovered) {
            updateAlertState(cityKey, now, fingerprint);
            return new AlertDecision(true, "RECOVERY");
        }

        if (isAnomaly && (!isDuplicateCondition || cooldownExpired)) {
            updateAlertState(cityKey, now, fingerprint);
            return new AlertDecision(true, "ANOMALY");
        }

        if (isSevere && (!isDuplicateCondition || cooldownExpired)) {
            updateAlertState(cityKey, now, fingerprint);
            return new AlertDecision(true, "ESCALATION");
        }

        if (isHighRisk && (!isDuplicateCondition || cooldownExpired)) {
            updateAlertState(cityKey, now, fingerprint);
            return new AlertDecision(true, "UPDATE");
        }

        return new AlertDecision(false, "NONE");
    }

    private void updateAlertState(String cityKey, long now, String fingerprint) {
        lastAlertTime.put(cityKey, now);
        lastAlertFingerprint.put(cityKey, fingerprint);
    }

    private String createFingerprint(CityMetrics m) {
        return String.format(
                "%s|%s|%s|%s",
                m.getRiskLevel(),
                safeRound(m.getAqiDeviationPercent()),
                m.getAqiTrend(),
                Boolean.TRUE.equals(m.getAnomaly())
        );
    }

    private String safeRound(Double value) {
        if (value == null) return "0";
        return String.valueOf(Math.round(value));
    }

    private int riskRank(String level) {
        return switch (level != null ? level : "NORMAL") {
            case "SEVERE" -> 4;
            case "HIGH_RISK" -> 3;
            case "ELEVATED" -> 2;
            default -> 1;
        };
    }

    private String buildFinalMessage(CityMetrics m, String alertType) {
        if ("RECOVERY".equals(alertType)) {
            return String.format(
                    "Recovery: %s returned to normal conditions (AQI %d, score %.1f).",
                    m.getCity(),
                    m.getAqi() != null ? m.getAqi() : 0,
                    m.getCityHealthScore() != null ? m.getCityHealthScore() : 0.0
            );
        }

        String base = m.getAlertMessage() != null ? m.getAlertMessage() :
                "Environmental update for " + m.getCity() + ".";

        if ("ESCALATION".equals(alertType)) {
            return "Escalation: " + base;
        }

        return base;
    }

    private record AlertDecision(boolean shouldAlert, String alertType) {
    }

    private String mapAlertState(String alertType) {
        return switch (alertType != null ? alertType : "NONE") {
            case "ESCALATION" -> "ESCALATED";
            case "RECOVERY" -> "RESOLVED";
            default -> "NEW";
        };
    }
}
