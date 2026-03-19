package com.urbanpulse.service;

import com.urbanpulse.model.BaselineMetrics;
import com.urbanpulse.model.ComparisonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Service for comparing current environmental data against baseline.
 * Computes deviation percentages, risk levels, and alert messages.
 */
@Service
public class ComparisonService {
    private static final Logger log = LoggerFactory.getLogger(ComparisonService.class);

    @Autowired
    private BaselineService baselineService;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final double NORMAL_THRESHOLD = 20.0;      // <= 20% deviation
    private static final double ELEVATED_THRESHOLD = 50.0;    // <= 50% deviation
    // > 50% deviation = HIGH_RISK

    /**
     * Compare current environmental metrics against city baseline.
     * 
     * @param city         City name
     * @param currentAqi   Current AQI value
     * @param currentPm25  Current PM2.5 value
     * @param currentPm10  Current PM10 value
     * @return Comparison result with deviation and risk assessment
     */
    public ComparisonResult compareWithBaseline(String city, Double currentAqi, 
                                                Double currentPm25, Double currentPm10) {
        
        Optional<BaselineMetrics> baseline = baselineService.getBaselineForCity(city);
        
        if (baseline.isEmpty()) {
            log.warn("No baseline available for city: {}", city);
            return createNoDataResult(city, "No baseline data available for " + city);
        }

        BaselineMetrics metrics = baseline.get();
        
        // Calculate AQI deviation percentage
        double aqiDeviation = calculateDeviationPercent(currentAqi, metrics.getAverageAqi());
        
        // Determine risk level
        String riskLevel = determineRiskLevel(aqiDeviation);
        
        // Generate alert message
        String alertMessage = generateAlertMessage(city, aqiDeviation, riskLevel);

        return ComparisonResult.builder()
                .city(city)
                .currentAqi(currentAqi)
                .baselineAqi(metrics.getAverageAqi())
                .aqiDeviationPercent(Math.round(aqiDeviation * 10.0) / 10.0)
                .pm25Current(currentPm25)
                .pm25Baseline(metrics.getAveragePm25())
                .pm10Current(currentPm10)
                .pm10Baseline(metrics.getAveragePm10())
                .riskLevel(riskLevel)
                .alertMessage(alertMessage)
                .timestamp(LocalDateTime.now().format(TIMESTAMP_FORMATTER))
                .build();
    }

    /**
     * Calculate deviation percentage between current and baseline value.
     * Formula: ((current - baseline) / baseline) * 100
     */
    private double calculateDeviationPercent(Double current, Double baseline) {
        if (baseline == null || baseline == 0) {
            return 0.0;
        }
        return ((current - baseline) / baseline) * 100.0;
    }

    /**
     * Determine risk level based on AQI deviation.
     */
    private String determineRiskLevel(double deviationPercent) {
        double absDeviation = Math.abs(deviationPercent);
        
        if (absDeviation <= NORMAL_THRESHOLD) {
            return "NORMAL";
        } else if (absDeviation <= ELEVATED_THRESHOLD) {
            return "ELEVATED";
        } else {
            return "HIGH_RISK";
        }
    }

    /**
     * Generate human-readable alert message based on deviation and risk.
     */
    private String generateAlertMessage(String city, double deviationPercent, String riskLevel) {
        switch (riskLevel) {
            case "NORMAL":
                return "Environmental conditions are normal for " + city + ".";
            case "ELEVATED":
                if (deviationPercent > 0) {
                    return "AQI is " + String.format("%.1f", Math.abs(deviationPercent)) + 
                           "% above baseline for " + city + ". Monitor the situation.";
                } else {
                    return "AQI is " + String.format("%.1f", Math.abs(deviationPercent)) + 
                           "% below baseline for " + city + ". Conditions improving.";
                }
            case "HIGH_RISK":
                if (deviationPercent > 0) {
                    return "ALERT: AQI is " + String.format("%.1f", Math.abs(deviationPercent)) + 
                           "% above baseline for " + city + ". High pollution risk!";
                } else {
                    return "NOTE: AQI is significantly below baseline. Unusual conditions detected.";
                }
            default:
                return "Unknown risk level.";
        }
    }

    /**
     * Create a result when no baseline data is available.
     */
    private ComparisonResult createNoDataResult(String city, String message) {
        return ComparisonResult.builder()
                .city(city)
                .riskLevel("UNKNOWN")
                .alertMessage(message)
                .timestamp(LocalDateTime.now().format(TIMESTAMP_FORMATTER))
                .build();
    }

}
