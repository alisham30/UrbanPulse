package com.urbanpulse.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket message sent to connected clients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessage {

    @JsonProperty("type")
    private String type; // "update", "alert", "heartbeat"

    @JsonProperty("city")
    private String city;

    @JsonProperty("data")
    private Object data; // CityMetrics or Alert

    @JsonProperty("timestamp")
    private Long timestamp;

}
