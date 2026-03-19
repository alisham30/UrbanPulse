package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * A single time-series data point capturing all key environmental readings for a city at a moment in time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSeriesReading implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("timestamp")
    private String timestamp;

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

    @JsonProperty("cityHealthScore")
    private Double cityHealthScore;

    @JsonProperty("riskLevel")
    private String riskLevel;
}
