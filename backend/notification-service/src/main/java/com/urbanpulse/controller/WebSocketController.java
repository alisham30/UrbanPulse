package com.urbanpulse.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * WebSocket controller for real-time communication.
 * Clients subscribe to /topic/city-updates to receive live updates.
 */
@Slf4j
@Controller
public class WebSocketController {

    /**
     * Handle client subscriptions.
     * Clients connect to /ws endpoint and subscribe to /topic/city-updates.
     */
    @MessageMapping("/subscribe")
    @SendTo("/topic/city-updates")
    public Map<String, Object> handleSubscription(String message) {
        log.debug("Client subscribed to city updates");
        return Map.of(
            "type", "subscription",
            "message", "Connected to city updates",
            "timestamp", System.currentTimeMillis()
        );
    }

}
