package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Individual environmental data record from historical data.
 * Represents one measurement point (city on a specific date).
 */
public class CityEnvironmentData {

    @JsonProperty("city")
    private String city;

    @JsonProperty("date")
    private LocalDate date;

    @JsonProperty("aqi")
    private Double aqi;

    @JsonProperty("pm25")
    private Double pm25;

    @JsonProperty("pm10")
    private Double pm10;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("humidity")
    private Double humidity;

    public CityEnvironmentData() {}

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Double getAqi() { return aqi; }
    public void setAqi(Double aqi) { this.aqi = aqi; }

    public Double getPm25() { return pm25; }
    public void setPm25(Double pm25) { this.pm25 = pm25; }

    public Double getPm10() { return pm10; }
    public void setPm10(Double pm10) { this.pm10 = pm10; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }

    /**
     * Validates if this record has the minimum required fields.
     */
    public boolean isValid() {
        return city != null && !city.trim().isEmpty() &&
               date != null &&
               aqi != null && aqi >= 0 &&
               pm25 != null && pm25 >= 0 &&
               pm10 != null && pm10 >= 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String city;
        private LocalDate date;
        private Double aqi;
        private Double pm25;
        private Double pm10;
        private Double temperature;
        private Double humidity;

        public Builder city(String city) { this.city = city; return this; }
        public Builder date(LocalDate date) { this.date = date; return this; }
        public Builder aqi(Double aqi) { this.aqi = aqi; return this; }
        public Builder pm25(Double pm25) { this.pm25 = pm25; return this; }
        public Builder pm10(Double pm10) { this.pm10 = pm10; return this; }
        public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
        public Builder humidity(Double humidity) { this.humidity = humidity; return this; }

        public CityEnvironmentData build() {
            CityEnvironmentData m = new CityEnvironmentData();
            m.city = this.city;
            m.date = this.date;
            m.aqi = this.aqi;
            m.pm25 = this.pm25;
            m.pm10 = this.pm10;
            m.temperature = this.temperature;
            m.humidity = this.humidity;
            return m;
        }
    }

}
