package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Latest city environmental metrics.
 * Stored in memory and pushed to frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CityMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("city")
    private String city;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("aqi")
    private Integer aqi;

    @JsonProperty("pm25")
    private Double pm25;

    @JsonProperty("pm10")
    private Double pm10;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("humidity")
    private Integer humidity;

    @JsonProperty("pressure")
    private Integer pressure;

    @JsonProperty("windSpeed")
    private Double windSpeed;

    @JsonProperty("cloudPercentage")
    private Integer cloudPercentage;

    @JsonProperty("weatherDescription")
    private String weatherDescription;

    @JsonProperty("baselineAqi")
    private Double baselineAqi;

    @JsonProperty("aqiDeviationPercent")
    private Double aqiDeviationPercent;

    @JsonProperty("cityHealthScore")
    private Double cityHealthScore;

    @JsonProperty("anomaly")
    private Boolean anomaly;

    @JsonProperty("riskLevel")
    private String riskLevel;

    @JsonProperty("alertMessage")
    private String alertMessage;

    // Extended intelligence fields
    @JsonProperty("rollingAqiAverage")
    private Double rollingAqiAverage;

    @JsonProperty("aqiTrend")
    private String aqiTrend; // RISING / FALLING / STABLE

    @JsonProperty("primaryDriver")
    private String primaryDriver; // AQI / PM2.5 / TEMPERATURE / HUMIDITY

    @JsonProperty("validationStatus")
    private String validationStatus; // MATCH / MINOR_DEVIATION / MAJOR_DEVIATION / UNAVAILABLE

    @JsonProperty("dataConfidenceScore")
    private Double dataConfidenceScore;

    @JsonProperty("openAqPm25")
    private Double openAqPm25;

    @JsonProperty("openAqPm10")
    private Double openAqPm10;

    /**
     * Human-readable, actionable recommendation computed by the Spark intelligence engine.
     */
    @JsonProperty("recommendation")
    private String recommendation;

    /**
     * Percentage change in AQI vs 10 readings ago. Null if insufficient history.
     * Formula: ((currentAQI - AQI_10_readings_ago) / AQI_10_readings_ago) * 100
     */
    @JsonProperty("aqiTimeChangePct")
    private Double aqiTimeChangePct;

}
