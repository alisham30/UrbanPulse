package com.urbanpulse.service;

import com.urbanpulse.model.AirPollutionResponse;
import com.urbanpulse.model.UnifiedPayload;
import com.urbanpulse.model.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Service for ingesting and normalizing environmental data.
 * Merges weather and air pollution data into unified payload.
 * Also calls OpenAQ for secondary validation of pollution readings.
 */
@Slf4j
@Service
public class DataIngestionService {

    @Autowired
    private OpenWeatherClient weatherClient;

    @Autowired
    private OpenAqClient openAqClient;

    private static final DateTimeFormatter ISO_FORMATTER = 
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));

    /**
     * Ingest data for a city: fetch weather + air pollution + merge.
     */
    public UnifiedPayload ingestCityData(String cityName, Double latitude, Double longitude) {
        try {
            log.info("Ingesting data for {} (lat={}, lon={})", cityName, latitude, longitude);

            // Fetch current weather
            WeatherResponse weather = weatherClient.getCurrentWeather(latitude, longitude);
            if (weather == null) {
                log.warn("Weather data is null for {}", cityName);
                return null;
            }

            // Fetch air pollution
            AirPollutionResponse pollution = weatherClient.getAirPollution(latitude, longitude);
            if (pollution == null) {
                log.warn("Air pollution data is null for {}", cityName);
                return null;
            }

            AirPollutionResponse.AirPollutionData pollData = pollution.getLatestData();
            if (pollData == null) {
                log.warn("No pollution data available for {}", cityName);
                return null;
            }

            // Merge into unified payload
            UnifiedPayload payload = mergeData(cityName, weather, pollData);

            // Enrich with OpenAQ secondary validation (non-blocking: any failure = UNAVAILABLE)
            try {
                OpenAqClient.OpenAqMeasurement openAq = openAqClient.getLatestMeasurements(latitude, longitude);
                payload.setOpenAqPm25(openAq != null ? openAq.pm25() : null);
                payload.setOpenAqPm10(openAq != null ? openAq.pm10() : null);
                payload.setValidationStatus(
                        openAqClient.computeValidationStatus(payload.getPm25(), payload.getPm10(), openAq));
                payload.setDataConfidenceScore(
                        openAqClient.computeConfidenceScore(payload.getPm25(), payload.getPm10(), openAq));
            } catch (Exception e) {
                log.debug("OpenAQ enrichment skipped for {}: {}", cityName, e.getMessage());
                payload.setValidationStatus("UNAVAILABLE");
                payload.setDataConfidenceScore(0.6);
            }

            log.debug("Successfully ingested data for {}: AQI={}, PM2.5={}, Temp={}", 
                    cityName, payload.getAqi(), payload.getPm25(), payload.getTemperature());

            return payload;

        } catch (Exception e) {
            log.error("Error ingesting data for city: {}", cityName, e);
            return null;
        }
    }

    /**
     * Merge weather and pollution data into unified payload.
     */
    private UnifiedPayload mergeData(String cityName, WeatherResponse weather, 
                                     AirPollutionResponse.AirPollutionData pollData) {
        
        // Get current timestamp
        String timestamp = Instant.now().atZone(ZoneId.of("UTC"))
                .format(ISO_FORMATTER);

        // Extract weather data
        Double temperature = weather.getMain() != null ? 
                weather.getMain().getTemperature() : null;
        Integer humidity = weather.getMain() != null ? 
                weather.getMain().getHumidity() : null;
        Integer pressure = weather.getMain() != null ? 
                weather.getMain().getPressure() : null;
        Double windSpeed = weather.getWind() != null ? 
                weather.getWind().getSpeed() : null;
        Integer cloudPercentage = weather.getClouds() != null ? 
                weather.getClouds().getCloudPercentage() : null;

        String weatherDesc = weather.getWeather() != null && !weather.getWeather().isEmpty() ?
                weather.getWeather().get(0).getDescription() : "unknown";

        // Extract pollution data
        Integer aqiLevel = pollData.getMain() != null ? 
                pollData.getMain().getAqi() : null;
        Double pm25 = pollData.getComponents() != null ? 
                pollData.getComponents().getPm25() : null;
        Double pm10 = pollData.getComponents() != null ? 
                pollData.getComponents().getPm10() : null;
        Double no2 = pollData.getComponents() != null ? 
                pollData.getComponents().getNo2() : null;
        Double o3 = pollData.getComponents() != null ? 
                pollData.getComponents().getO3() : null;
        Double so2 = pollData.getComponents() != null ? 
                pollData.getComponents().getSo2() : null;
        Double co = pollData.getComponents() != null ? 
                pollData.getComponents().getCo() : null;

        // Normalize AQI to 0-500 scale (from 1-5 to approximate 0-500)
        Integer normalizedAqi = null;
        if (aqiLevel != null) {
            normalizedAqi = switch (aqiLevel) {
                case 1 -> 50;   // 1-50: Good
                case 2 -> 100;  // 51-100: Fair
                case 3 -> 150;  // 101-150: Moderate
                case 4 -> 250;  // 151-250: Poor
                case 5 -> 350;  // 251+: Very Poor
                default -> 0;
            };
        }

        return UnifiedPayload.builder()
                .city(cityName)
                .timestamp(timestamp)
                .latitude(weather.getCoordinates() != null ? 
                        weather.getCoordinates().getLatitude() : 0.0)
                .longitude(weather.getCoordinates() != null ? 
                        weather.getCoordinates().getLongitude() : 0.0)
                .temperature(temperature)
                .humidity(humidity)
                .pressure(pressure)
                .windSpeed(windSpeed)
                .cloudPercentage(cloudPercentage)
                .weatherDescription(weatherDesc)
                .aqi(normalizedAqi)
                .pm25(pm25)
                .pm10(pm10)
                .no2(no2)
                .o3(o3)
                .so2(so2)
                .co(co)
                .source("openweather")
                .apiVersion("2.5")
                .build();
    }

}
