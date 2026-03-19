package com.urbanpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * UrbanPulse Baseline Service
 * 
 * Loads historical environmental data from CSV and provides baseline metrics
 * for city environmental analysis.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class BaselineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaselineServiceApplication.class, args);
    }

}
