package com.urbanpulse.controller;

import com.urbanpulse.entity.CityMetricsEntity;
import com.urbanpulse.model.Alert;
import com.urbanpulse.model.CityMetrics;
import com.urbanpulse.model.TimeSeriesReading;
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

    /**
     * GET /api/timeseries/{city} - Full 50-reading time-series for a city.
     */
    @GetMapping("/timeseries/{city}")
    public ResponseEntity<?> getTimeSeries(@PathVariable String city) {
        try {
            List<TimeSeriesReading> series = metricsStoreService.getTimeSeries(city);
            Map<String, Object> response = new HashMap<>();
            response.put("city", city);
            response.put("count", series.size());
            response.put("series", series);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving time series for city: {}", city, e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/top-risk - City with the lowest health score right now.
     */
    @GetMapping("/top-risk")
    public ResponseEntity<?> getTopRiskCity() {
        try {
            return metricsStoreService.getTopRiskCity()
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(404).body(Map.of("message", "No metrics available yet")));
        } catch (Exception e) {
            log.error("Error retrieving top-risk city", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/most-improved - City with the most improving trend.
     */
    @GetMapping("/most-improved")
    public ResponseEntity<?> getMostImprovedCity() {
        try {
            return metricsStoreService.getMostImprovedCity()
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(404).body(Map.of("message", "No improving city found yet")));
        } catch (Exception e) {
            log.error("Error retrieving most-improved city", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/history/{city}/range?range=1h|6h|24h — Time-range filtered history from MySQL.
     */
    @GetMapping("/history/{city}/range")
    public ResponseEntity<?> getHistoryWithRange(
            @PathVariable String city,
            @RequestParam(defaultValue = "6h") String range) {
        try {
            long hoursBack = parseRangeToHours(range);
            java.time.Instant end = java.time.Instant.now();
            java.time.Instant start = end.minus(hoursBack, java.time.temporal.ChronoUnit.HOURS);

            List<CityMetricsEntity> history = metricsStoreService.getHistoryFromDb(city, start, end);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("city", city);
            response.put("range", range);
            response.put("count", history.size());
            response.put("from", start.toString());
            response.put("to", end.toString());
            response.put("history", history);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving ranged history for {}", city, e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/compare?cities=Mumbai,Delhi,Chennai — Multi-city comparison data.
     */
    @GetMapping("/compare")
    public ResponseEntity<?> compareCities(@RequestParam String cities) {
        try {
            String[] cityNames = cities.split(",");
            if (cityNames.length < 2 || cityNames.length > 5) {
                return ResponseEntity.badRequest().body(Map.of("error", "Provide 2-5 city names separated by commas"));
            }

            Map<String, Object> comparisonData = new LinkedHashMap<>();
            List<Map<String, Object>> cityDataList = new ArrayList<>();
            String bestCity = null;
            double bestScore = -1;
            String worstCity = null;
            double worstScore = 101;

            for (String name : cityNames) {
                String trimmed = name.trim();
                Optional<CityMetrics> metricsOpt = metricsStoreService.getMetrics(trimmed);
                if (metricsOpt.isPresent()) {
                    CityMetrics m = metricsOpt.get();
                    Map<String, Object> cityData = new LinkedHashMap<>();
                    cityData.put("city", m.getCity());
                    cityData.put("aqi", m.getAqi());
                    cityData.put("pm25", m.getPm25());
                    cityData.put("pm10", m.getPm10());
                    cityData.put("temperature", m.getTemperature());
                    cityData.put("humidity", m.getHumidity());
                    cityData.put("windSpeed", m.getWindSpeed());
                    cityData.put("cityHealthScore", m.getCityHealthScore());
                    cityData.put("riskLevel", m.getRiskLevel());
                    cityData.put("aqiTrend", m.getAqiTrend());
                    cityData.put("aqiDeviationPercent", m.getAqiDeviationPercent());
                    cityData.put("dataConfidenceScore", m.getDataConfidenceScore());
                    cityData.put("validationStatus", m.getValidationStatus());
                    cityData.put("recommendation", m.getRecommendation());
                    cityData.put("primaryDriver", m.getPrimaryDriver());
                    cityData.put("baselineAqi", m.getBaselineAqi());
                    cityData.put("latitude", m.getLatitude());
                    cityData.put("longitude", m.getLongitude());
                    cityDataList.add(cityData);

                    double score = m.getCityHealthScore() != null ? m.getCityHealthScore() : 0;
                    if (score > bestScore) {
                        bestScore = score;
                        bestCity = m.getCity();
                    }
                    if (score < worstScore) {
                        worstScore = score;
                        worstCity = m.getCity();
                    }
                }
            }

            comparisonData.put("count", cityDataList.size());
            comparisonData.put("cities", cityDataList);
            comparisonData.put("bestCity", bestCity);
            comparisonData.put("worstCity", worstCity);

            return ResponseEntity.ok(comparisonData);
        } catch (Exception e) {
            log.error("Error comparing cities", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/rankings — All cities ranked by health score.
     */
    @GetMapping("/rankings")
    public ResponseEntity<?> getCityRankings() {
        try {
            Map<String, CityMetrics> allMetrics = metricsStoreService.getAllMetrics();
            List<Map<String, Object>> rankings = allMetrics.values().stream()
                    .filter(m -> m.getCityHealthScore() != null)
                    .sorted(Comparator.comparingDouble(CityMetrics::getCityHealthScore))
                    .map(m -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("city", m.getCity());
                        entry.put("cityHealthScore", m.getCityHealthScore());
                        entry.put("aqi", m.getAqi());
                        entry.put("riskLevel", m.getRiskLevel());
                        entry.put("aqiTrend", m.getAqiTrend());
                        entry.put("dataConfidenceScore", m.getDataConfidenceScore());
                        return entry;
                    })
                    .toList();

            return ResponseEntity.ok(Map.of("count", rankings.size(), "rankings", rankings));
        } catch (Exception e) {
            log.error("Error retrieving city rankings", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    private long parseRangeToHours(String range) {
        return switch (range.toLowerCase()) {
            case "1h" -> 1;
            case "6h" -> 6;
            case "12h" -> 12;
            case "24h" -> 24;
            case "48h" -> 48;
            case "7d" -> 168;
            default -> 6;
        };
    }

}
