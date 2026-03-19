package com.urbanpulse.service;

import com.urbanpulse.model.WeatherResponse;
import com.urbanpulse.model.AirPollutionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for fetching real-time data from OpenWeather APIs.
 * 
 * Two endpoints are used:
 * 1. Current Weather: /data/2.5/weather
 * 2. Air Pollution: /data/2.5/air_pollution
 */
@Slf4j
@Service
public class OpenWeatherClient {

    @Autowired
    private WebClient webClient;

    @Value("${urbanpulse.ingestion.openweather.api-key:test-key}")
    private String apiKey;

    @Value("${urbanpulse.ingestion.openweather.base-url:https://api.openweathermap.org}")
    private String baseUrl;

    /**
     * Fetch current weather data for given coordinates.
     */
    public WeatherResponse getCurrentWeather(Double latitude, Double longitude) {
        if (apiKey == null || apiKey.isBlank() || "your-api-key-here".equals(apiKey)) {
            log.warn("OpenWeather API key is missing. Set OPENWEATHER_API_KEY to enable ingestion.");
            return null;
        }
        try {
            log.debug("Fetching current weather for lat={}, lon={}", latitude, longitude);

            return webClient.get()
                    .uri(baseUrl + "/data/2.5/weather" +
                            "?lat={lat}&lon={lon}&appid={key}&units=metric",
                            latitude, longitude, apiKey)
                    .retrieve()
                    .bodyToMono(WeatherResponse.class)
                    .block(); // Synchronous call for simplicity

        } catch (Exception e) {
            log.error("Error fetching weather data for lat={}, lon={}", latitude, longitude, e);
            return null;
        }
    }

    /**
     * Fetch air pollution data for given coordinates.
     */
    public AirPollutionResponse getAirPollution(Double latitude, Double longitude) {
        if (apiKey == null || apiKey.isBlank() || "your-api-key-here".equals(apiKey)) {
            log.warn("OpenWeather API key is missing. Set OPENWEATHER_API_KEY to enable ingestion.");
            return null;
        }
        try {
            log.debug("Fetching air pollution data for lat={}, lon={}", latitude, longitude);

            return webClient.get()
                    .uri(baseUrl + "/data/2.5/air_pollution" +
                            "?lat={lat}&lon={lon}&appid={key}",
                            latitude, longitude, apiKey)
                    .retrieve()
                    .bodyToMono(AirPollutionResponse.class)
                    .block(); // Synchronous call for simplicity

        } catch (Exception e) {
            log.error("Error fetching air pollution data for lat={}, lon={}", latitude, longitude, e);
            return null;
        }
    }

}
