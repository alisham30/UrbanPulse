package com.urbanpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UrbanPulse Ingestion Service
 * 
 * Fetches real-time environmental data from OpenWeather APIs
 * and publishes to Kafka topic for downstream processing.
 */
@SpringBootApplication
@EnableScheduling
public class IngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionServiceApplication.class, args);
    }

}
