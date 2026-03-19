package com.urbanpulse.service;

import com.urbanpulse.model.BaselineMetrics;
import com.urbanpulse.model.CityEnvironmentData;
import com.urbanpulse.util.EnvironmentCsvParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing historical environmental baseline metrics.
 * Loads baseline data on startup and provides aggregated statistics per city.
 */
@Service
public class BaselineService {
    private static final Logger log = LoggerFactory.getLogger(BaselineService.class);

    @Autowired
    private EnvironmentCsvParser csvParser;

    @Value("${urbanpulse.baseline.csv-path:../../data/city_day.csv}")
    private String csvPath;

    private Map<String, BaselineMetrics> baselinesByCity = new HashMap<>();
    private List<CityEnvironmentData> allHistoricalData = new ArrayList<>();

    /**
     * Initialize baseline on application startup.
     * Loads CSV and computes aggregated metrics per city.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing baseline service with CSV: {}", csvPath);
        
        try {
            // Parse CSV
            allHistoricalData = csvParser.parseCSV(csvPath);
            log.info("Loaded {} environmental records from CSV", allHistoricalData.size());

            // Compute baselines per city
            computeBaselines();
            
            log.info("Computed baselines for {} cities", baselinesByCity.size());
            baselinesByCity.forEach((city, metrics) ->
                log.info("Baseline for {}: AQI avg={}, PM2.5 avg={}, records={}",
                        city, metrics.getAverageAqi(), metrics.getAveragePm25(), metrics.getRecordCount()));

        } catch (Exception e) {
            log.error("Error initializing baseline service", e);
        }
    }

    /**
     * Compute aggregated baseline metrics per city from historical data.
     */
    private void computeBaselines() {
        Map<String, List<CityEnvironmentData>> groupedByCity = allHistoricalData.stream()
                .collect(Collectors.groupingBy(CityEnvironmentData::getCity));

        for (Map.Entry<String, List<CityEnvironmentData>> entry : groupedByCity.entrySet()) {
            String cityKey = entry.getKey();
            List<CityEnvironmentData> data = entry.getValue();

            if (!data.isEmpty()) {
                BaselineMetrics metrics = computeMetricsForCity(cityKey, data);
                baselinesByCity.put(cityKey, metrics);
            }
        }
    }

    /**
     * Compute aggregated metrics for a specific city.
     */
    private BaselineMetrics computeMetricsForCity(String normalizedCity, List<CityEnvironmentData> cityData) {
        DoubleSummaryStatistics aqiStats = cityData.stream()
                .mapToDouble(CityEnvironmentData::getAqi)
                .summaryStatistics();

        double avgPm25 = cityData.stream()
                .mapToDouble(CityEnvironmentData::getPm25)
                .average()
                .orElse(0.0);

        double avgPm10 = cityData.stream()
                .mapToDouble(CityEnvironmentData::getPm10)
                .average()
                .orElse(0.0);

        double avgTemperature = cityData.stream()
                .filter(d -> d.getTemperature() != null)
                .mapToDouble(CityEnvironmentData::getTemperature)
                .average()
                .orElse(0.0);

        double avgHumidity = cityData.stream()
                .filter(d -> d.getHumidity() != null)
                .mapToDouble(CityEnvironmentData::getHumidity)
                .average()
                .orElse(0.0);

        String displayCity = csvParser.toTitleCase(normalizedCity);

        return BaselineMetrics.builder()
                .city(displayCity)
                .averageAqi(Math.round(aqiStats.getAverage() * 10.0) / 10.0)
                .minAqi(aqiStats.getMin())
                .maxAqi(aqiStats.getMax())
                .averagePm25(Math.round(avgPm25 * 10.0) / 10.0)
                .averagePm10(Math.round(avgPm10 * 10.0) / 10.0)
                .temperatureAverage(Math.round(avgTemperature * 10.0) / 10.0)
                .humidityAverage(Math.round(avgHumidity * 10.0) / 10.0)
                .recordCount((long) cityData.size())
                .build();
    }

    /**
     * Get all available city baselines.
     */
    public Map<String, BaselineMetrics> getAllBaselines() {
        return new HashMap<>(baselinesByCity);
    }

    /**
     * Get baseline metrics for a specific city.
     */
    public Optional<BaselineMetrics> getBaselineForCity(String city) {
        String normalizedCity = city.trim().toLowerCase();
        return Optional.ofNullable(baselinesByCity.get(normalizedCity));
    }

    /**
     * Check if a city has baseline data available.
     */
    public boolean hasBaselineForCity(String city) {
        String normalizedCity = city.trim().toLowerCase();
        return baselinesByCity.containsKey(normalizedCity);
    }

    /**
     * Get list of all available cities in baseline data.
     */
    public List<String> getAvailableCities() {
        return baselinesByCity.values().stream()
                .map(BaselineMetrics::getCity)
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

}
