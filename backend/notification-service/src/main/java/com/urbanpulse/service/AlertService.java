package com.urbanpulse.service;

import com.urbanpulse.entity.AlertEntity;
import com.urbanpulse.model.Alert;
import com.urbanpulse.repository.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Hybrid alert service: in-memory bounded queue + MySQL persistence.
 */
@Slf4j
@Service
public class AlertService {

    @Value("${urbanpulse.notification.max-alerts:100}")
    private int maxAlerts;

    @Value("${urbanpulse.notification.max-city-alerts:20}")
    private int maxCityAlerts;

    private final ConcurrentLinkedQueue<Alert> alerts = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Deque<Alert>> alertsByCity = new ConcurrentHashMap<>();

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public void addAlert(Alert alert) {
        if (alert == null) {
            log.warn("Attempted to add null alert");
            return;
        }

        alerts.offer(alert);
        addCityAlert(alert);

        while (alerts.size() > maxAlerts) {
            alerts.poll();
        }

        persistAlert(alert);
        log.debug("Alert added for {}: {}", alert.getCity(), alert.getRiskLevel());
    }

    private void persistAlert(Alert a) {
        try {
            Instant recordedAt;
            try {
                recordedAt = a.getTimestamp() != null ? Instant.parse(a.getTimestamp()) : Instant.now();
            } catch (Exception e) {
                recordedAt = Instant.now();
            }

            AlertEntity entity = AlertEntity.builder()
                    .alertId(a.getId())
                    .city(a.getCity() != null ? a.getCity().toLowerCase() : "unknown")
                    .recordedAt(recordedAt)
                    .riskLevel(a.getRiskLevel())
                    .message(a.getMessage())
                    .aqi(a.getAqi())
                    .cityHealthScore(a.getCityHealthScore())
                    .primaryDriver(a.getPrimaryDriver())
                    .alertType(a.getAlertType())
                    .alertState(a.getAlertState())
                    .build();

            alertRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist alert to MySQL: {}", e.getMessage());
        }
    }

    public List<Alert> getRecentAlerts(int limit) {
        List<Alert> result = new ArrayList<>(alerts);
        Collections.reverse(result);
        return result.stream().limit(limit).toList();
    }

    public List<Alert> getAlertsForCity(String city) {
        if (city == null) {
            return Collections.emptyList();
        }

        String normalizedCity = city.toLowerCase(Locale.ROOT);
        Deque<Alert> queue = alertsByCity.get(normalizedCity);
        if (queue == null) return Collections.emptyList();

        List<Alert> cityAlerts = new ArrayList<>(queue);
        Collections.reverse(cityAlerts);
        return cityAlerts;
    }

    public List<Alert> getAllAlerts() {
        return new ArrayList<>(alerts);
    }

    public void clearAll() {
        alerts.clear();
        alertsByCity.clear();
        log.info("Cleared all in-memory alerts");
    }

    public int getAlertCount() {
        return alerts.size();
    }

    private void addCityAlert(Alert alert) {
        if (alert.getCity() == null) return;

        String key = alert.getCity().toLowerCase(Locale.ROOT);
        Deque<Alert> queue = alertsByCity.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(alert);
            while (queue.size() > maxCityAlerts) {
                queue.removeFirst();
            }
        }
    }
}
