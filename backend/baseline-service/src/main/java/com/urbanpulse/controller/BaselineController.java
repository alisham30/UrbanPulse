package com.urbanpulse.controller;

import com.urbanpulse.model.BaselineMetrics;
import com.urbanpulse.model.ComparisonResult;
import com.urbanpulse.service.BaselineService;
import com.urbanpulse.service.ComparisonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API controller for baseline and comparison endpoints.
 * 
 * Endpoints:
 * - GET /api/baseline - Get all baselines
 * - GET /api/baseline/{city} - Get baseline for specific city
 * - POST /api/compare - Compare current metrics against baseline
 * - GET /api/cities - List available cities
 */
@RestController
@RequestMapping("/api")
public class BaselineController {
    private static final Logger log = LoggerFactory.getLogger(BaselineController.class);

    @Autowired
    private BaselineService baselineService;

    @Autowired
    private ComparisonService comparisonService;

    /**
     * GET /api/baseline - Get all city baselines.
     */
    @GetMapping("/baseline")
    public ResponseEntity<?> getAllBaselines() {
        try {
            Map<String, BaselineMetrics> baselines = baselineService.getAllBaselines();
            if (baselines.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "message", "No baseline data available",
                    "data", baselines
                ));
            }
            return ResponseEntity.ok(baselines);
        } catch (Exception e) {
            log.error("Error fetching all baselines", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/baseline/{city} - Get baseline for specific city.
     */
    @GetMapping("/baseline/{city}")
    public ResponseEntity<?> getBaselineForCity(@PathVariable String city) {
        try {
            Optional<BaselineMetrics> baseline = baselineService.getBaselineForCity(city);
            if (baseline.isPresent()) {
                return ResponseEntity.ok(baseline.get());
            } else {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "No baseline data for city: " + city,
                    "availableCities", baselineService.getAvailableCities()
                ));
            }
        } catch (Exception e) {
            log.error("Error fetching baseline for city: {}", city, e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * POST /api/compare - Compare current environmental data against baseline.
     * 
     * Request body:
     * {
     *   "city": "Mumbai",
     *   "currentAqi": 145,
     *   "currentPm25": 61.2,
     *   "currentPm10": 89.5
     * }
     */
    @PostMapping("/compare")
    public ResponseEntity<?> compareWithBaseline(@RequestBody Map<String, Object> request) {
        try {
            String city = (String) request.get("city");
            Double currentAqi = ((Number) request.get("currentAqi")).doubleValue();
            Double currentPm25 = ((Number) request.get("currentPm25")).doubleValue();
            Double currentPm10 = ((Number) request.get("currentPm10")).doubleValue();

            if (city == null || city.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "City name is required"));
            }

            if (currentAqi == null || currentPm25 == null || currentPm10 == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "currentAqi, currentPm25, and currentPm10 are required"));
            }

            ComparisonResult result = comparisonService.compareWithBaseline(
                    city, currentAqi, currentPm25, currentPm10);

            return ResponseEntity.ok(result);

        } catch (ClassCastException | NullPointerException e) {
            log.warn("Invalid request body format", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid request format"));
        } catch (Exception e) {
            log.error("Error comparing with baseline", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * GET /api/cities - Get list of available cities with baseline data.
     */
    @GetMapping("/cities")
    public ResponseEntity<?> getAvailableCities() {
        try {
            List<String> cities = baselineService.getAvailableCities();
            Map<String, Object> response = new HashMap<>();
            response.put("count", cities.size());
            response.put("cities", cities);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching available cities", e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "baseline-service",
            "timestamp", System.currentTimeMillis()
        ));
    }

}
