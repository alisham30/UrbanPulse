package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alert record for recent events.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @JsonProperty("id")
    private String id;

    @JsonProperty("city")
    private String city;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("riskLevel")
    private String riskLevel; // NORMAL, ELEVATED, HIGH_RISK

    @JsonProperty("message")
    private String message;

    @JsonProperty("aqi")
    private Integer aqi;

    @JsonProperty("cityHealthScore")
    private Double cityHealthScore;

    @JsonProperty("primaryDriver")
    private String primaryDriver;

    @JsonProperty("alertType")
    private String alertType;

    /**
     * Lifecycle state of this alert: NEW, ESCALATED, or RESOLVED.
     */
    @JsonProperty("alertState")
    private String alertState;

}
