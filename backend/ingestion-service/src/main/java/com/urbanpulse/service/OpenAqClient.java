package com.urbanpulse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Objects;

/**
 * Client for fetching secondary pollution data from OpenAQ v3 API.
 * Used to validate and enrich OpenWeather pollution readings.
 */
@Slf4j
@Service
public class OpenAqClient {

    @Value("${urbanpulse.ingestion.openaq.api-key:}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAqClient() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openaq.org/v3")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Represents the result of an OpenAQ location lookup.
     */
    public record OpenAqMeasurement(
            Double pm25,
            Double pm10,
            String locationName
    ) {
    }

    /**
     * Fetch latest PM2.5 and PM10 measurements from OpenAQ near the given coordinates.
     * Returns null if OpenAQ is unavailable, no data found, or any error occurs.
     */
    public OpenAqMeasurement getLatestMeasurements(double lat, double lon) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("OpenAQ API key not configured; skipping validation");
            return null;
        }

        try {
            String responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/locations")
                            .queryParam("coordinates", lat + "," + lon)
                            .queryParam("radius", 25000)
                            .queryParam("limit", 1)
                            .build())
                    .header("X-API-Key", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(6))
                    .block();

            return parseResponse(responseBody);

        } catch (Exception e) {
            log.debug("OpenAQ unavailable for lat={}, lon={}: {}", lat, lon, e.getMessage());
            return null;
        }
    }

    /**
     * Parse OpenAQ response and extract PM2.5 and PM10 values.
     */
    private OpenAqMeasurement parseResponse(String body) {
        if (body == null || body.isBlank()) return null;

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode results = root.path("results");

            if (!results.isArray() || results.isEmpty()) {
                log.debug("OpenAQ returned no results");
                return null;
            }

            JsonNode location = results.get(0);
            String locationName = location.path("name").asText("unknown");

            JsonNode parameters = location.path("parameters");
            if (!parameters.isArray() || parameters.isEmpty()) {
                parameters = location.path("sensors");
            }

            Double pm25 = null;
            Double pm10 = null;

            for (JsonNode param : parameters) {
                String name = param.path("name").asText("");
                if (name.isEmpty()) {
                    name = param.path("parameter").path("name").asText("");
                }
                double val = param.path("lastValue").asDouble(Double.NaN);
                if (Double.isNaN(val)) {
                    val = param.path("value").asDouble(Double.NaN);
                }
                if (Double.isNaN(val) || val < 0) continue;

                if ("pm25".equalsIgnoreCase(name) || "pm2.5".equalsIgnoreCase(name)) {
                    pm25 = val;
                } else if ("pm10".equalsIgnoreCase(name)) {
                    pm10 = val;
                }
            }

            if (pm25 == null && pm10 == null) {
                log.debug("OpenAQ location '{}' has no PM2.5/PM10 data", locationName);
                return null;
            }

            log.info("OpenAQ data for '{}': pm25={}, pm10={}", locationName, pm25, pm10);
            return new OpenAqMeasurement(pm25, pm10, locationName);

        } catch (Exception e) {
            log.debug("Failed to parse OpenAQ response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Compute validation status by comparing OpenWeather and OpenAQ PM values.
     */
    public String computeValidationStatus(Double owPm25, Double owPm10, OpenAqMeasurement openAq) {
        if (openAq == null || (openAq.pm25() == null && openAq.pm10() == null)) {
            return "UNAVAILABLE";
        }

        double avgDeviation = averageDeviationPercent(owPm25, owPm10, openAq.pm25(), openAq.pm10());
        if (Double.isNaN(avgDeviation)) return "UNAVAILABLE";

        if (avgDeviation <= 15.0) return "MATCH";
        if (avgDeviation <= 40.0) return "MINOR_DEVIATION";
        return "MAJOR_DEVIATION";
    }

    /**
     * Compute data confidence score (0.0 to 1.0).
     */
    public double computeConfidenceScore(Double owPm25, Double owPm10, OpenAqMeasurement openAq) {
        if (openAq == null || (openAq.pm25() == null && openAq.pm10() == null)) {
            return 0.6;
        }

        double deviation = averageDeviationPercent(owPm25, owPm10, openAq.pm25(), openAq.pm10());
        if (Double.isNaN(deviation)) return 0.6;

        double score = Math.max(0.4, 1.0 - (deviation / 150.0));
        return Math.round(score * 100.0) / 100.0;
    }

    private double averageDeviationPercent(Double owPm25, Double owPm10, Double openPm25, Double openPm10) {
        double sum = 0.0;
        int count = 0;

        Double pm25Dev = singleDeviation(owPm25, openPm25);
        if (pm25Dev != null) {
            sum += pm25Dev;
            count++;
        }

        Double pm10Dev = singleDeviation(owPm10, openPm10);
        if (pm10Dev != null) {
            sum += pm10Dev;
            count++;
        }

        if (count == 0) return Double.NaN;
        return sum / count;
    }

    private Double singleDeviation(Double source, Double reference) {
        if (Objects.isNull(source) || Objects.isNull(reference) || source <= 0) {
            return null;
        }
        return Math.abs(source - reference) / source * 100.0;
    }
}
