package com.urbanpulse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for fetching secondary pollution data from Open-Meteo Air Quality API (free, no key).
 * Used to cross-validate and enrich OpenWeather pollution readings.
 */
@Slf4j
@Service
public class OpenAqClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Cache: "lat,lon" -> {measurement, timestampMs} — avoid hammering API every 30s */
    private final Map<String, CachedMeasurement> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    private record CachedMeasurement(OpenAqMeasurement measurement, long fetchedAt) {}

    public OpenAqClient() {
        this.webClient = WebClient.builder()
                .baseUrl("https://air-quality-api.open-meteo.com")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public record OpenAqMeasurement(
            Double pm25,
            Double pm10,
            String locationName
    ) {
    }

    /**
     * Fetch current PM2.5, PM10, and US AQI from Open-Meteo Air Quality API.
     * Completely free, no API key required, returns real-time model-based data per coordinate.
     */
    public OpenAqMeasurement getLatestMeasurements(double lat, double lon) {
        String cacheKey = String.format("%.2f,%.2f", lat, lon);

        CachedMeasurement cached = cache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.fetchedAt()) < CACHE_TTL_MS) {
            log.debug("Air quality cache hit for {}", cacheKey);
            return cached.measurement();
        }

        try {
            String responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/air-quality")
                            .queryParam("latitude", lat)
                            .queryParam("longitude", lon)
                            .queryParam("current", "pm2_5,pm10,us_aqi")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();

            OpenAqMeasurement result = parseOpenMeteoResponse(responseBody, lat, lon);
            cache.put(cacheKey, new CachedMeasurement(result, System.currentTimeMillis()));

            if (result != null) {
                log.info("Open-Meteo AQ near ({},{}): pm25={}, pm10={}, source='{}'",
                        lat, lon, result.pm25(), result.pm10(), result.locationName());
            }
            return result;

        } catch (Exception e) {
            log.warn("Open-Meteo AQ unavailable for lat={}, lon={}: {}", lat, lon, e.getMessage());
            return null;
        }
    }

    /**
     * Parse Open-Meteo Air Quality response.
     * Shape: { "current": { "pm2_5": 32.5, "pm10": 93.1, "us_aqi": 122 }, ... }
     */
    private OpenAqMeasurement parseOpenMeteoResponse(String body, double lat, double lon) {
        if (body == null || body.isBlank()) return null;

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode current = root.path("current");

            if (current.isMissingNode()) {
                log.debug("Open-Meteo response has no 'current' node");
                return null;
            }

            Double pm25 = current.has("pm2_5") && !current.get("pm2_5").isNull()
                    ? current.get("pm2_5").asDouble() : null;
            Double pm10 = current.has("pm10") && !current.get("pm10").isNull()
                    ? current.get("pm10").asDouble() : null;

            if (pm25 == null && pm10 == null) {
                log.debug("Open-Meteo returned null PM values for ({},{})", lat, lon);
                return null;
            }

            String locationLabel = String.format("Open-Meteo[%.2f,%.2f]", lat, lon);
            return new OpenAqMeasurement(pm25, pm10, locationLabel);

        } catch (Exception e) {
            log.debug("Failed to parse Open-Meteo response: {}", e.getMessage());
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
     * Compute data confidence score (0.0 to 1.0) based on cross-validation with OpenAQ.
     * When OpenAQ is available: score derived from measurement deviation.
     * When unavailable: returns null so the caller can compute a signal-based fallback.
     */
    public Double computeConfidenceScore(Double owPm25, Double owPm10, OpenAqMeasurement openAq) {
        if (openAq == null || (openAq.pm25() == null && openAq.pm10() == null)) {
            return null; // Let the caller compute signal-based confidence
        }

        double deviation = averageDeviationPercent(owPm25, owPm10, openAq.pm25(), openAq.pm10());
        if (Double.isNaN(deviation)) return null;

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
