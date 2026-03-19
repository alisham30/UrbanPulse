package com.urbanpulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Web client configuration for HTTP requests to OpenWeather APIs.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl("https://api.openweathermap.org")
                .build();
    }

}
