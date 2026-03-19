package com.urbanpulse.controller;

import com.urbanpulse.model.Alert;
import com.urbanpulse.model.CityMetrics;
import com.urbanpulse.service.AlertService;
import com.urbanpulse.service.MetricsStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API endpoints for dashboard and data retrieval.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {

    @Autowired
    private MetricsStoreService metricsStoreService;

    @Autowired
    private AlertService alertService;

    /**
     * GET /api/latest - Get latest metrics for all cities.
     */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestMetrics(@RequestParam(value = "city", required = false) String city) {
        try {
            if (city != null && !city.isBlank()) {
                Optional<CityMetrics> metrics = metricsStoreService.getMetrics(city);
                if (metrics.isPresent()) return ResponseEntity.ok(metrics.get());
                return ResponseEntity.status(404).body(Map.of(
                        "error", "No metrics available for city: " + city,
                        "availableCities", metricsStoreService.getAvailableCities()
                ));
            }
            Map<String, CityMetrics> allMetrics = metricsStoreService.getAllMetrics();
            return ResponseEntity.ok(allMetrics);
        } catch (Exception e) {
            log.error("Error retrieving latest metrics", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/latest/{city} - Get latest metrics for a specific city.
     */
    @GetMapping("/latest/{city}")
    public ResponseEntity<?> getLatestMetricsForCity(@PathVariable String city) {
        try {
            Optional<CityMetrics> metrics = metricsStoreService.getMetrics(city);
            if (metrics.isPresent()) {
                return ResponseEntity.ok(metrics.get());
            } else {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "No metrics available for city: " + city,
                    "availableCities", metricsStoreService.getAvailableCities()
                ));
            }
        } catch (Exception e) {
            log.error("Error retrieving metrics for city: {}", city, e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/alerts - Get recent alerts.
     */
    @GetMapping("/alerts")
    public ResponseEntity<?> getAlerts(@RequestParam(defaultValue = "20") int limit,
                                       @RequestParam(value = "city", required = false) String city) {
        try {
            List<Alert> alerts = (city == null || city.isBlank())
                    ? alertService.getRecentAlerts(limit)
                    : alertService.getAlertsForCity(city);
            if (limit > 0 && alerts.size() > limit) {
                alerts = alerts.stream().limit(limit).toList();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("count", alerts.size());
            response.put("alerts", alerts);
            if (city != null && !city.isBlank()) response.put("city", city);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving alerts", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/alerts/{city} - Get alerts for a specific city.
     */
    @GetMapping("/alerts/{city}")
    public ResponseEntity<?> getAlertsForCity(@PathVariable String city) {
        try {
            List<Alert> alerts = alertService.getAlertsForCity(city);
            Map<String, Object> response = new HashMap<>();
            response.put("city", city);
            response.put("count", alerts.size());
            response.put("alerts", alerts);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving alerts for city: {}", city, e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/cities - Get list of cities with available metrics.
     */
    @GetMapping("/cities")
    public ResponseEntity<?> getAvailableCities() {
        try {
            List<String> cities = metricsStoreService.getAvailableCities();
            Map<String, Object> response = new HashMap<>();
            response.put("count", cities.size());
            response.put("cities", cities);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving available cities", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/dashboard - Dashboard summary data.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardData() {
        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, CityMetrics> allMetrics = metricsStoreService.getAllMetrics();
            List<Alert> recentAlerts = alertService.getRecentAlerts(10);

            // Calculate summary statistics
            double avgHealthScore = allMetrics.values().stream()
                    .mapToDouble(m -> m.getCityHealthScore() != null ? m.getCityHealthScore() : 0)
                    .average()
                    .orElse(0);

            long highRiskCount = allMetrics.values().stream()
                    .filter(m -> "HIGH_RISK".equals(m.getRiskLevel()))
                    .count();

            long elevatedCount = allMetrics.values().stream()
                    .filter(m -> "ELEVATED".equals(m.getRiskLevel()))
                    .count();

            response.put("summary", Map.of(
                "citiesMonitored", allMetrics.size(),
                "averageHealthScore", Math.round(avgHealthScore * 10.0) / 10.0,
                "highRiskCities", highRiskCount,
                "elevatedCities", elevatedCount
            ));
            response.put("cities", allMetrics);
            response.put("recentAlerts", recentAlerts);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving dashboard data", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/history/{city} - Get AQI history for a specific city (last 20 readings).
     */
    @GetMapping("/history")
    public ResponseEntity<?> getAqiHistory(@RequestParam(value = "city") String city) {
        try {
            List<Map<String, Object>> history = metricsStoreService.getAqiHistory(city);
            Map<String, Object> response = new HashMap<>();
            response.put("city", city);
            response.put("count", history.size());
            response.put("history", history);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving AQI history for city: {}", city, e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Backward-compatible path variant.
     */
    @GetMapping("/history/{city}")
    public ResponseEntity<?> getAqiHistoryPath(@PathVariable String city) {
        return getAqiHistory(city);
    }

    /**
     * GET /api/health - Health check.
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "notification-service",
            "timestamp", System.currentTimeMillis(),
            "metricsCount", metricsStoreService.getMetricsCount(),
            "alertsCount", alertService.getAlertCount()
        ));
    }

}
