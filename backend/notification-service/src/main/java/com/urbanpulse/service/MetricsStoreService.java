package com.urbanpulse.service;

import com.urbanpulse.model.CityMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for the latest city metrics and AQI history.
 * Stores the most recent metrics per city plus a rolling window of AQI readings.
 */
@Slf4j
@Service
public class MetricsStoreService {

    private static final int MAX_HISTORY = 20;

    private final ConcurrentHashMap<String, CityMetrics> metricsStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedList<Map<String, Object>>> aqiHistory = new ConcurrentHashMap<>();

    public void storeMetrics(CityMetrics metrics) {
        if (metrics == null || metrics.getCity() == null) {
            log.warn("Cannot store null metrics or metrics without city");
            return;
        }
        String key = metrics.getCity().toLowerCase();
        metricsStore.put(key, metrics);
        log.debug("Stored metrics for city: {}", metrics.getCity());
    }

    public void addAqiHistory(String city, String timestamp, Integer aqi) {
        if (city == null || aqi == null) return;
        String key = city.toLowerCase();
        aqiHistory.computeIfAbsent(key, k -> new LinkedList<>());
        LinkedList<Map<String, Object>> history = aqiHistory.get(key);
        synchronized (history) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", timestamp != null ? timestamp : "");
            entry.put("aqi", aqi);
            history.addLast(entry);
            if (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
    }

    public List<Map<String, Object>> getAqiHistory(String city) {
        if (city == null) return Collections.emptyList();
        LinkedList<Map<String, Object>> history = aqiHistory.get(city.toLowerCase());
        if (history == null) return Collections.emptyList();
        synchronized (history) {
            return new ArrayList<>(history);
        }
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

    public int getMetricsCount() {
        return metricsStore.size();
    }

    public void clearAll() {
        metricsStore.clear();
        aqiHistory.clear();
        log.info("Cleared all metrics");
    }
}
