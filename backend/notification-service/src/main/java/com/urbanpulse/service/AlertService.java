package com.urbanpulse.service;

import com.urbanpulse.model.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service for managing alert history.
 * Maintains recent alerts in memory (bounded queue).
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

    /**
     * Add a new alert.
     */
    public void addAlert(Alert alert) {
        if (alert == null) {
            log.warn("Attempted to add null alert");
            return;
        }

        alerts.offer(alert);
        addCityAlert(alert);

        // Maintain size limit
        while (alerts.size() > maxAlerts) {
            alerts.poll();
        }

        log.debug("Alert added for {}: {}", alert.getCity(), alert.getRiskLevel());
    }

    /**
     * Get recent alerts (most recent first).
     */
    public List<Alert> getRecentAlerts(int limit) {
        List<Alert> result = new ArrayList<>(alerts);
        Collections.reverse(result);
        return result.stream().limit(limit).toList();
    }

    /**
     * Get all alerts for a specific city.
     */
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

    /**
     * Get all alerts.
     */
    public List<Alert> getAllAlerts() {
        return new ArrayList<>(alerts);
    }

    /**
     * Clear all alerts (useful for testing).
     */
    public void clearAll() {
        alerts.clear();
        alertsByCity.clear();
        log.info("Cleared all alerts");
    }

    /**
     * Get alert count.
     */
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
