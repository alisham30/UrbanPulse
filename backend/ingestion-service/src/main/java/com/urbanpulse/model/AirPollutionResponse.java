package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenWeather Air Pollution API Response DTO
 * Parsed from: /data/2.5/air_pollution?lat={lat}&lon={lon}&appid={key}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AirPollutionResponse {

    @JsonProperty("coord")
    private Coordinates coordinates;

    @JsonProperty("list")
    private List<AirPollutionData> list;

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
    public static class AirPollutionData {
        @JsonProperty("dt")
        private Long dt; // Unix timestamp

        @JsonProperty("main")
        private MainAQI main;

        @JsonProperty("components")
        private AirComponents components;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class MainAQI {
            @JsonProperty("aqi")
            private Integer aqi; // 1=Good, 2=Fair, 3=Moderate, 4=Poor, 5=Very Poor
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class AirComponents {
            @JsonProperty("pm2_5")
            private Double pm25; // PM2.5 in μg/m³

            @JsonProperty("pm10")
            private Double pm10; // PM10 in μg/m³

            @JsonProperty("no2")
            private Double no2;

            @JsonProperty("o3")
            private Double o3;

            @JsonProperty("so2")
            private Double so2;

            @JsonProperty("co")
            private Double co;
        }
    }

    /**
     * Get the first (most recent) air pollution data point if available.
     */
    public AirPollutionData getLatestData() {
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

}
