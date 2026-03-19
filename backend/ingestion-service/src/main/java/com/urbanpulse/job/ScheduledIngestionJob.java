package com.urbanpulse.job;

import com.urbanpulse.model.UnifiedPayload;
import com.urbanpulse.service.DataIngestionService;
import com.urbanpulse.service.KafkaProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scheduled job to fetch environmental data for all configured cities.
 * Runs periodically (every 30 seconds by default) and publishes to Kafka.
 */
@Slf4j
@Component
@EnableConfigurationProperties(ScheduledIngestionJob.CityConfig.class)
public class ScheduledIngestionJob {

    @Autowired
    private DataIngestionService dataIngestionService;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired(required = false)
    private CityConfig cityConfig;

    @PostConstruct
    public void logConfiguredCities() {
        if (cityConfig == null || cityConfig.getCities() == null || cityConfig.getCities().isEmpty()) {
            log.warn("No cities configured for ingestion");
            return;
        }
        String cityList = cityConfig.getCities().stream()
                .map(c -> c.getName() + " (" + c.getLatitude() + "," + c.getLongitude() + ")")
                .collect(Collectors.joining(", "));
        log.info("Configured ingestion cities: {}", cityList);
    }

    /**
     * Interval mode scheduler.
     */
    @Scheduled(fixedDelayString = "${urbanpulse.ingestion.schedule.interval-ms:30000}")
    public void ingestAndPublishInterval() {
        if (cityConfig != null && Boolean.FALSE.equals(cityConfig.getUseInterval())) {
            return;
        }
        ingestAndPublish();
    }

    /**
     * Cron mode scheduler.
     */
    @Scheduled(cron = "${urbanpulse.ingestion.schedule.cron:0/30 * * * * ?}")
    public void ingestAndPublishCron() {
        if (cityConfig == null || !Boolean.FALSE.equals(cityConfig.getUseInterval())) {
            return;
        }
        ingestAndPublish();
    }

    private void ingestAndPublish() {
        try {
            log.debug("Starting scheduled ingestion job");

            if (cityConfig == null || cityConfig.getCities() == null || cityConfig.getCities().isEmpty()) {
                log.warn("No cities configured for ingestion");
                return;
            }

            for (City city : cityConfig.getCities()) {
                ingestCity(city);
            }

            log.debug("Completed scheduled ingestion job");

        } catch (Exception e) {
            log.error("Error in scheduled ingestion job", e);
        }
    }

    /**
     * Ingest data for a single city and publish to Kafka.
     */
    private void ingestCity(City city) {
        try {
            UnifiedPayload payload = dataIngestionService.ingestCityData(
                    city.getName(), city.getLatitude(), city.getLongitude());

            if (payload != null) {
                kafkaProducerService.publishData(payload);
            } else {
                log.warn("Failed to ingest data for {}: payload is null", city.getName());
            }

        } catch (Exception e) {
            log.error("Error ingesting city {}", city.getName(), e);
        }
    }

    /**
     * Configuration for cities to ingest.
     */
    @ConfigurationProperties(prefix = "urbanpulse.ingestion")
    public static class CityConfig {
        private List<City> cities;
        private Boolean useInterval = true;

        public List<City> getCities() {
            return cities;
        }

        public void setCities(List<City> cities) {
            this.cities = cities;
        }

        public Boolean getUseInterval() {
            return useInterval;
        }

        public void setUseInterval(Boolean useInterval) {
            this.useInterval = useInterval;
        }
    }

    /**
     * City coordinates configuration.
     */
    public static class City {
        private String name;
        private Double latitude;
        private Double longitude;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }

        @Override
        public String toString() {
            return name + " (" + latitude + "," + longitude + ")";
        }
    }

}
