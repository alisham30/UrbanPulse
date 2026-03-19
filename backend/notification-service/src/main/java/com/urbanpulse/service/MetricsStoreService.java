package com.urbanpulse.service;

import com.urbanpulse.entity.CityMetricsEntity;
import com.urbanpulse.model.CityMetrics;
import com.urbanpulse.model.TimeSeriesReading;
import com.urbanpulse.repository.CityMetricsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hybrid store: in-memory cache for real-time speed + MySQL write-through for persistence.
 * Keeps the most recent 50 full-field readings per city in-memory.
 */
@Slf4j
@Service
public class MetricsStoreService {

    private static final int MAX_HISTORY = 50;

    private final ConcurrentHashMap<String, CityMetrics> metricsStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedList<TimeSeriesReading>> timeSeriesStore = new ConcurrentHashMap<>();

    private final CityMetricsRepository metricsRepository;

    public MetricsStoreService(CityMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    public void storeMetrics(CityMetrics metrics) {
        if (metrics == null || metrics.getCity() == null) {
            log.warn("Cannot store null metrics or metrics without city");
            return;
        }
        String key = metrics.getCity().toLowerCase();
        metricsStore.put(key, metrics);

        addTimeSeriesReading(metrics);
        persistToDatabase(metrics);

        log.debug("Stored metrics for city: {}", metrics.getCity());
    }

    private void persistToDatabase(CityMetrics m) {
        try {
            Instant recordedAt = parseTimestamp(m.getTimestamp());

            CityMetricsEntity entity = CityMetricsEntity.builder()
                    .city(m.getCity().toLowerCase())
                    .recordedAt(recordedAt)
                    .latitude(m.getLatitude())
                    .longitude(m.getLongitude())
                    .aqi(m.getAqi())
                    .pm25(m.getPm25())
                    .pm10(m.getPm10())
                    .temperature(m.getTemperature())
                    .humidity(m.getHumidity())
                    .pressure(m.getPressure())
                    .windSpeed(m.getWindSpeed())
                    .cloudPercentage(m.getCloudPercentage())
                    .weatherDescription(m.getWeatherDescription())
                    .baselineAqi(m.getBaselineAqi())
                    .aqiDeviationPercent(m.getAqiDeviationPercent())
                    .cityHealthScore(m.getCityHealthScore())
                    .anomaly(m.getAnomaly())
                    .riskLevel(m.getRiskLevel())
                    .alertMessage(m.getAlertMessage())
                    .rollingAqiAverage(m.getRollingAqiAverage())
                    .aqiTrend(m.getAqiTrend())
                    .primaryDriver(m.getPrimaryDriver())
                    .validationStatus(m.getValidationStatus())
                    .dataConfidenceScore(m.getDataConfidenceScore())
                    .openAqPm25(m.getOpenAqPm25())
                    .openAqPm10(m.getOpenAqPm10())
                    .recommendation(m.getRecommendation())
                    .aqiTimeChangePct(m.getAqiTimeChangePct())
                    .build();

            metricsRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist metrics to MySQL for {}: {}", m.getCity(), e.getMessage());
        }
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(timestamp));
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }

    private void addTimeSeriesReading(CityMetrics metrics) {
        String key = metrics.getCity().toLowerCase();
        timeSeriesStore.computeIfAbsent(key, k -> new LinkedList<>());
        LinkedList<TimeSeriesReading> series = timeSeriesStore.get(key);
        synchronized (series) {
            series.addLast(TimeSeriesReading.builder()
                    .timestamp(metrics.getTimestamp() != null ? metrics.getTimestamp() : "")
                    .aqi(metrics.getAqi())
                    .pm25(metrics.getPm25())
                    .pm10(metrics.getPm10())
                    .temperature(metrics.getTemperature())
                    .humidity(metrics.getHumidity())
                    .cityHealthScore(metrics.getCityHealthScore())
                    .riskLevel(metrics.getRiskLevel())
                    .build());
            if (series.size() > MAX_HISTORY) {
                series.removeFirst();
            }
        }
    }

    public void addAqiHistory(String city, String timestamp, Integer aqi) {
        // No-op — storeMetrics() already adds a full reading for every update.
    }

    public List<TimeSeriesReading> getTimeSeries(String city) {
        if (city == null) return Collections.emptyList();
        LinkedList<TimeSeriesReading> series = timeSeriesStore.get(city.toLowerCase());
        if (series == null) return Collections.emptyList();
        synchronized (series) {
            return new ArrayList<>(series);
        }
    }

    /** Get time-range filtered history from MySQL. */
    public List<CityMetricsEntity> getHistoryFromDb(String city, Instant start, Instant end) {
        return metricsRepository.findByCityIgnoreCaseAndRecordedAtBetweenOrderByRecordedAtDesc(
                city.toLowerCase(), start, end);
    }

    public List<Map<String, Object>> getAqiHistory(String city) {
        List<TimeSeriesReading> series = getTimeSeries(city);
        List<Map<String, Object>> result = new ArrayList<>(series.size());
        for (TimeSeriesReading r : series) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", r.getTimestamp());
            entry.put("aqi", r.getAqi());
            result.add(entry);
        }
        return result;
    }

    public Optional<CityMetrics> getMetrics(String city) {
        if (city == null) return Optional.empty();
        return Optional.ofNullable(metricsStore.get(city.toLowerCase()));
    }

    public Map<String, CityMetrics> getAllMetrics() {
        return new HashMap<>(metricsStore);
    }

    public List<String> getAvailableCities() {
        return metricsStore.values().stream()
                .map(CityMetrics::getCity)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    public Optional<CityMetrics> getTopRiskCity() {
        return metricsStore.values().stream()
                .filter(m -> m.getCityHealthScore() != null)
                .min(Comparator.comparingDouble(CityMetrics::getCityHealthScore));
    }

    public Optional<CityMetrics> getMostImprovedCity() {
        return metricsStore.values().stream()
                .filter(m -> "FALLING".equals(m.getAqiTrend()) && m.getAqiDeviationPercent() != null)
                .min(Comparator.comparingDouble(CityMetrics::getAqiDeviationPercent));
    }

    public int getMetricsCount() {
        return metricsStore.size();
    }

    public void clearAll() {
        metricsStore.clear();
        timeSeriesStore.clear();
        log.info("Cleared all in-memory metrics");
    }
}
