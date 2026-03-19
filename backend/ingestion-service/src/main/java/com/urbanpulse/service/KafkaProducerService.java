package com.urbanpulse.service;

import com.urbanpulse.model.UnifiedPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Service for publishing environmental data to Kafka.
 * Publishes unified payloads to raw-city-data topic for Spark consumption.
 */
@Slf4j
@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, UnifiedPayload> kafkaTemplate;

    @Value("${spring.kafka.topic.raw-city-data}")
    private String topic;

    @Value("${spring.kafka.topic.weather-raw-events}")
    private String weatherTopic;

    @Value("${spring.kafka.topic.pollution-raw-events}")
    private String pollutionTopic;

    @Value("${spring.kafka.topic.openaq-validation-events}")
    private String openaqTopic;

    /**
     * Publish unified environmental data to Kafka topic.
     * Also fans out to granular per-source topics for multi-stream Spark consumption.
     */
    public void publishData(UnifiedPayload payload) {
        if (payload == null) {
            log.warn("Cannot publish null payload");
            return;
        }

        try {
            String key = payload.getCity() != null ? 
                    payload.getCity().toLowerCase() : "unknown";

            Message<UnifiedPayload> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(KafkaHeaders.TOPIC, topic)
                    .setHeader(KafkaHeaders.KEY, key)
                    .setHeader("city", payload.getCity())
                    .build();

            kafkaTemplate.send(message)
                    .thenAccept(result -> 
                        log.info("Published data for {} to Kafka. Topic: {}, Partition: {}, Offset: {}",
                            payload.getCity(), 
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset())
                    )
                    .exceptionally(ex -> {
                        log.error("Failed to publish data for {}", payload.getCity(), ex);
                        return null;
                    });

            // Fan out to granular topics for multi-stream Spark consumption
            sendToTopic(payload, key, weatherTopic);
            sendToTopic(payload, key, pollutionTopic);

            // Publish OpenAQ validation event only when data is available
            if (payload.getValidationStatus() != null && !"UNAVAILABLE".equals(payload.getValidationStatus())) {
                sendToTopic(payload, key, openaqTopic);
            }

        } catch (Exception e) {
            log.error("Error publishing payload to Kafka", e);
        }
    }

    private void sendToTopic(UnifiedPayload payload, String key, String targetTopic) {
        try {
            Message<UnifiedPayload> msg = MessageBuilder
                    .withPayload(payload)
                    .setHeader(KafkaHeaders.TOPIC, targetTopic)
                    .setHeader(KafkaHeaders.KEY, key)
                    .build();
            kafkaTemplate.send(msg)
                    .exceptionally(ex -> {
                        log.error("Failed to publish to {}", targetTopic, ex);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Error sending to topic {}", targetTopic, e);
        }
    }

}
