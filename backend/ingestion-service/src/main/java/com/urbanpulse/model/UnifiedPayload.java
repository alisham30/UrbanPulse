package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Unified environmental data payload.
 * Merges weather and air pollution data from OpenWeather APIs.
 * This flows through Kafka and is consumed by Spark for analytics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("city")
    private String city;

    @JsonProperty("timestamp")
    private String timestamp; // ISO 8601 format: 2026-03-18T22:30:00Z

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    // Weather data
    @JsonProperty("temperature")
    private Double temperature; // Celsius

    @JsonProperty("humidity")
    private Integer humidity; // 0-100%

    @JsonProperty("pressure")
    private Integer pressure; // hectopascals

    @JsonProperty("windSpeed")
    private Double windSpeed; // m/s

    @JsonProperty("cloudPercentage")
    private Integer cloudPercentage; // 0-100%

    @JsonProperty("weatherDescription")
    private String weatherDescription;

    // Air pollution data
    @JsonProperty("aqi")
    private Integer aqi; // OpenWeather AQI: 1-5 scale

    @JsonProperty("pm25")
    private Double pm25; // μg/m³

    @JsonProperty("pm10")
    private Double pm10; // μg/m³

    @JsonProperty("no2")
    private Double no2; // nitrogen dioxide

    @JsonProperty("o3")
    private Double o3; // ozone

    @JsonProperty("so2")
    private Double so2; // sulfur dioxide

    @JsonProperty("co")
    private Double co; // carbon monoxide

    // OpenAQ validation fields (secondary source)
    @JsonProperty("openAqPm25")
    private Double openAqPm25; // PM2.5 from OpenAQ μg/m³

    @JsonProperty("openAqPm10")
    private Double openAqPm10; // PM10 from OpenAQ μg/m³

    @JsonProperty("validationStatus")
    private String validationStatus; // MATCH / MINOR_DEVIATION / MAJOR_DEVIATION / UNAVAILABLE

    @JsonProperty("dataConfidenceScore")
    private Double dataConfidenceScore; // 0.0 to 1.0

    // Metadata
    @JsonProperty("source")
    private String source; // "openweather"

    @JsonProperty("apiVersion")
    private String apiVersion; // "2.5"

}
