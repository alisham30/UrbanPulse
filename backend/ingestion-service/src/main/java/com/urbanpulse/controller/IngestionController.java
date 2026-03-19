package com.urbanpulse.controller;

import com.urbanpulse.model.UnifiedPayload;
import com.urbanpulse.job.ScheduledIngestionJob;
import com.urbanpulse.service.DataIngestionService;
import com.urbanpulse.service.KafkaProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for manual ingestion triggers.
 * Allows on-demand data fetching and publishing (useful for testing).
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class IngestionController {

    @Autowired
    private DataIngestionService dataIngestionService;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired(required = false)
    private ScheduledIngestionJob.CityConfig cityConfig;

    /**
     * POST /api/ingest - Manually trigger data ingestion for a city.
     * 
     * Request body:
     * {
     *   "city": "Mumbai",
     *   "latitude": 19.0760,
     *   "longitude": 72.8777
     * }
     */
    @PostMapping("/ingest")
    public ResponseEntity<?> manualIngest(@RequestBody Map<String, Object> request) {
        try {
            String city = (String) request.get("city");
            Number latitudeNumber = (Number) request.get("latitude");
            Number longitudeNumber = (Number) request.get("longitude");
            Double latitude = latitudeNumber != null ? latitudeNumber.doubleValue() : null;
            Double longitude = longitudeNumber != null ? longitudeNumber.doubleValue() : null;

            if (city == null || city.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "City name is required"));
            }

            if (latitude == null || longitude == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Latitude and longitude are required"));
            }

            log.info("Manual ingestion request for {} ({}, {})", city, latitude, longitude);

            UnifiedPayload payload = dataIngestionService.ingestCityData(city, latitude, longitude);

            if (payload != null) {
                kafkaProducerService.publishData(payload);
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Data ingested and published for " + city,
                    "data", payload
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Failed to ingest data for " + city
                ));
            }

        } catch (Exception e) {
            log.error("Error in manual ingestion", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Internal server error"
            ));
        }
    }

    /**
     * GET /api/health - Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "ingestion-service",
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * GET /api/cities - list currently configured ingestion cities.
     */
    @GetMapping("/cities")
    public ResponseEntity<?> getConfiguredCities() {
        if (cityConfig == null || cityConfig.getCities() == null) {
            return ResponseEntity.ok(Map.of("count", 0, "cities", List.of()));
        }

        List<Map<String, Object>> cities = cityConfig.getCities().stream()
                .map(c -> Map.<String, Object>of(
                        "name", c.getName(),
                        "latitude", c.getLatitude(),
                        "longitude", c.getLongitude()))
                .toList();

        return ResponseEntity.ok(Map.of("count", cities.size(), "cities", cities));
    }

}
