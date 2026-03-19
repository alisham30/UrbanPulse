package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenWeather Current Weather API Response DTO
 * Parsed from: /data/2.5/weather?lat={lat}&lon={lon}&appid={key}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherResponse {

    @JsonProperty("coord")
    private Coordinates coordinates;

    @JsonProperty("main")
    private MainMetrics main;

    @JsonProperty("weather")
    private List<WeatherDescription> weather;

    @JsonProperty("wind")
    private WindData wind;

    @JsonProperty("clouds")
    private Clouds clouds;

    @JsonProperty("dt")
    private Long dt; // Unix timestamp

    @JsonProperty("name")
    private String cityName;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Coordinates {
        @JsonProperty("lon")
        private Double longitude;

        @JsonProperty("lat")
        private Double latitude;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MainMetrics {
        @JsonProperty("temp")
        private Double temperature; // in Kelvin, needs conversion to Celsius

        @JsonProperty("humidity")
        private Integer humidity; // 0-100%

        @JsonProperty("pressure")
        private Integer pressure;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeatherDescription {
        @JsonProperty("id")
        private Integer id;

        @JsonProperty("main")
        private String main;

        @JsonProperty("description")
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WindData {
        @JsonProperty("speed")
        private Double speed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Clouds {
        @JsonProperty("all")
        private Integer cloudPercentage;
    }

}
