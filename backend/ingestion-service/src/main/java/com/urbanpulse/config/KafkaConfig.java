package com.urbanpulse.config;

import com.urbanpulse.model.UnifiedPayload;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for UrbanPulse Ingestion Service.
 * Configures producer and auto-creates topics.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.topic.raw-city-data}")
    private String rawCityDataTopic;

    @Value("${spring.kafka.topic.weather-raw-events}")
    private String weatherRawEventsTopic;

    @Value("${spring.kafka.topic.pollution-raw-events}")
    private String pollutionRawEventsTopic;

    @Value("${spring.kafka.topic.openaq-validation-events}")
    private String openaqValidationEventsTopic;

    @Value("${spring.kafka.topic.city-intelligence-events}")
    private String cityIntelligenceEventsTopic;

    @Value("${spring.kafka.topic.city-alert-events}")
    private String cityAlertEventsTopic;

    /**
     * Kafka Admin configuration for creating topics.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean public NewTopic rawCityDataNewTopic() { return new NewTopic(rawCityDataTopic, 3, (short) 1); }
    @Bean public NewTopic weatherRawEventsNewTopic() { return new NewTopic(weatherRawEventsTopic, 3, (short) 1); }
    @Bean public NewTopic pollutionRawEventsNewTopic() { return new NewTopic(pollutionRawEventsTopic, 3, (short) 1); }
    @Bean public NewTopic openaqValidationEventsNewTopic() { return new NewTopic(openaqValidationEventsTopic, 3, (short) 1); }
    @Bean public NewTopic cityIntelligenceEventsNewTopic() { return new NewTopic(cityIntelligenceEventsTopic, 3, (short) 1); }
    @Bean public NewTopic cityAlertEventsNewTopic() { return new NewTopic(cityAlertEventsTopic, 3, (short) 1); }

    /**
     * Kafka Producer Factory configuration.
     */
    @Bean
    public ProducerFactory<String, UnifiedPayload> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka Template for sending messages.
     */
    @Bean
    @SuppressWarnings("null")
    public KafkaTemplate<String, UnifiedPayload> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

}
