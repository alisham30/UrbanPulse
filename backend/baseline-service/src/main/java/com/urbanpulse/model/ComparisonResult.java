package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of comparing current metrics against baseline.
 * Includes deviation analysis and risk assessment.
 */
public class ComparisonResult {

    @JsonProperty("city")
    private String city;

    @JsonProperty("currentAqi")
    private Double currentAqi;

    @JsonProperty("baselineAqi")
    private Double baselineAqi;

    @JsonProperty("aqiDeviationPercent")
    private Double aqiDeviationPercent;

    @JsonProperty("pm25Current")
    private Double pm25Current;

    @JsonProperty("pm25Baseline")
    private Double pm25Baseline;

    @JsonProperty("pm10Current")
    private Double pm10Current;

    @JsonProperty("pm10Baseline")
    private Double pm10Baseline;

    @JsonProperty("riskLevel")
    private String riskLevel; // NORMAL, ELEVATED, HIGH_RISK

    @JsonProperty("alertMessage")
    private String alertMessage;

    @JsonProperty("timestamp")
    private String timestamp;

    public ComparisonResult() {}

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Double getCurrentAqi() { return currentAqi; }
    public void setCurrentAqi(Double currentAqi) { this.currentAqi = currentAqi; }

    public Double getBaselineAqi() { return baselineAqi; }
    public void setBaselineAqi(Double baselineAqi) { this.baselineAqi = baselineAqi; }

    public Double getAqiDeviationPercent() { return aqiDeviationPercent; }
    public void setAqiDeviationPercent(Double aqiDeviationPercent) { this.aqiDeviationPercent = aqiDeviationPercent; }

    public Double getPm25Current() { return pm25Current; }
    public void setPm25Current(Double pm25Current) { this.pm25Current = pm25Current; }

    public Double getPm25Baseline() { return pm25Baseline; }
    public void setPm25Baseline(Double pm25Baseline) { this.pm25Baseline = pm25Baseline; }

    public Double getPm10Current() { return pm10Current; }
    public void setPm10Current(Double pm10Current) { this.pm10Current = pm10Current; }

    public Double getPm10Baseline() { return pm10Baseline; }
    public void setPm10Baseline(Double pm10Baseline) { this.pm10Baseline = pm10Baseline; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String city;
        private Double currentAqi;
        private Double baselineAqi;
        private Double aqiDeviationPercent;
        private Double pm25Current;
        private Double pm25Baseline;
        private Double pm10Current;
        private Double pm10Baseline;
        private String riskLevel;
        private String alertMessage;
        private String timestamp;

        public Builder city(String city) { this.city = city; return this; }
        public Builder currentAqi(Double currentAqi) { this.currentAqi = currentAqi; return this; }
        public Builder baselineAqi(Double baselineAqi) { this.baselineAqi = baselineAqi; return this; }
        public Builder aqiDeviationPercent(Double aqiDeviationPercent) { this.aqiDeviationPercent = aqiDeviationPercent; return this; }
        public Builder pm25Current(Double pm25Current) { this.pm25Current = pm25Current; return this; }
        public Builder pm25Baseline(Double pm25Baseline) { this.pm25Baseline = pm25Baseline; return this; }
        public Builder pm10Current(Double pm10Current) { this.pm10Current = pm10Current; return this; }
        public Builder pm10Baseline(Double pm10Baseline) { this.pm10Baseline = pm10Baseline; return this; }
        public Builder riskLevel(String riskLevel) { this.riskLevel = riskLevel; return this; }
        public Builder alertMessage(String alertMessage) { this.alertMessage = alertMessage; return this; }
        public Builder timestamp(String timestamp) { this.timestamp = timestamp; return this; }

        public ComparisonResult build() {
            ComparisonResult m = new ComparisonResult();
            m.city = this.city;
            m.currentAqi = this.currentAqi;
            m.baselineAqi = this.baselineAqi;
            m.aqiDeviationPercent = this.aqiDeviationPercent;
            m.pm25Current = this.pm25Current;
            m.pm25Baseline = this.pm25Baseline;
            m.pm10Current = this.pm10Current;
            m.pm10Baseline = this.pm10Baseline;
            m.riskLevel = this.riskLevel;
            m.alertMessage = this.alertMessage;
            m.timestamp = this.timestamp;
            return m;
        }
    }
}
