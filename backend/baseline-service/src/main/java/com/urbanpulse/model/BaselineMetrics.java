package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Aggregated baseline metrics for a single city.
 * Computed from historical environmental data.
 */
public class BaselineMetrics {

    @JsonProperty("city")
    private String city;

    @JsonProperty("averageAqi")
    private Double averageAqi;

    @JsonProperty("averagePm25")
    private Double averagePm25;

    @JsonProperty("averagePm10")
    private Double averagePm10;

    @JsonProperty("minAqi")
    private Double minAqi;

    @JsonProperty("maxAqi")
    private Double maxAqi;

    @JsonProperty("recordCount")
    private Long recordCount;

    @JsonProperty("temperatureAverage")
    private Double temperatureAverage;

    @JsonProperty("humidityAverage")
    private Double humidityAverage;

    public BaselineMetrics() {}

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Double getAverageAqi() { return averageAqi; }
    public void setAverageAqi(Double averageAqi) { this.averageAqi = averageAqi; }

    public Double getAveragePm25() { return averagePm25; }
    public void setAveragePm25(Double averagePm25) { this.averagePm25 = averagePm25; }

    public Double getAveragePm10() { return averagePm10; }
    public void setAveragePm10(Double averagePm10) { this.averagePm10 = averagePm10; }

    public Double getMinAqi() { return minAqi; }
    public void setMinAqi(Double minAqi) { this.minAqi = minAqi; }

    public Double getMaxAqi() { return maxAqi; }
    public void setMaxAqi(Double maxAqi) { this.maxAqi = maxAqi; }

    public Long getRecordCount() { return recordCount; }
    public void setRecordCount(Long recordCount) { this.recordCount = recordCount; }

    public Double getTemperatureAverage() { return temperatureAverage; }
    public void setTemperatureAverage(Double temperatureAverage) { this.temperatureAverage = temperatureAverage; }

    public Double getHumidityAverage() { return humidityAverage; }
    public void setHumidityAverage(Double humidityAverage) { this.humidityAverage = humidityAverage; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String city;
        private Double averageAqi;
        private Double averagePm25;
        private Double averagePm10;
        private Double minAqi;
        private Double maxAqi;
        private Long recordCount;
        private Double temperatureAverage;
        private Double humidityAverage;

        public Builder city(String city) { this.city = city; return this; }
        public Builder averageAqi(Double averageAqi) { this.averageAqi = averageAqi; return this; }
        public Builder averagePm25(Double averagePm25) { this.averagePm25 = averagePm25; return this; }
        public Builder averagePm10(Double averagePm10) { this.averagePm10 = averagePm10; return this; }
        public Builder minAqi(Double minAqi) { this.minAqi = minAqi; return this; }
        public Builder maxAqi(Double maxAqi) { this.maxAqi = maxAqi; return this; }
        public Builder recordCount(Long recordCount) { this.recordCount = recordCount; return this; }
        public Builder temperatureAverage(Double temperatureAverage) { this.temperatureAverage = temperatureAverage; return this; }
        public Builder humidityAverage(Double humidityAverage) { this.humidityAverage = humidityAverage; return this; }

        public BaselineMetrics build() {
            BaselineMetrics m = new BaselineMetrics();
            m.city = this.city;
            m.averageAqi = this.averageAqi;
            m.averagePm25 = this.averagePm25;
            m.averagePm10 = this.averagePm10;
            m.minAqi = this.minAqi;
            m.maxAqi = this.maxAqi;
            m.recordCount = this.recordCount;
            m.temperatureAverage = this.temperatureAverage;
            m.humidityAverage = this.humidityAverage;
            return m;
        }
    }
}
