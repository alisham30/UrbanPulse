package com.urbanpulse.service;

import com.urbanpulse.repository.AlertRepository;
import com.urbanpulse.repository.CityMetricsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class DataRetentionService {

    private final CityMetricsRepository metricsRepository;
    private final AlertRepository alertRepository;

    @Value("${urbanpulse.retention.days:7}")
    private int retentionDays;

    public DataRetentionService(CityMetricsRepository metricsRepository, AlertRepository alertRepository) {
        this.metricsRepository = metricsRepository;
        this.alertRepository = alertRepository;
    }

    @Scheduled(fixedDelayString = "${urbanpulse.retention.cleanup-interval-ms:21600000}")
    @Transactional
    public void cleanupOldData() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        int metricsDeleted = metricsRepository.deleteOlderThan(cutoff);
        int alertsDeleted = alertRepository.deleteOlderThan(cutoff);

        if (metricsDeleted > 0 || alertsDeleted > 0) {
            log.info("Retention cleanup: removed {} metrics and {} alerts older than {} days",
                    metricsDeleted, alertsDeleted, retentionDays);
        }
    }
}
