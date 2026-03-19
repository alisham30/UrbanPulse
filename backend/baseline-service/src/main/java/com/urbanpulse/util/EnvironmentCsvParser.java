package com.urbanpulse.util;

import com.urbanpulse.model.CityEnvironmentData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for parsing historical Environmental CSV data.
 * Handles Kaggle air quality dataset format.
 */
@Component
public class EnvironmentCsvParser {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentCsvParser.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<String> CITY_TITLE_EXCEPTIONS = List.of("NCR", "Bengaluru", "Bangalore");

    /**
     * Parse a CSV file and return list of CityEnvironmentData records.
     * 
     * Expected CSV columns:
     * - City (or location name)
     * - Date
     * - AQI
     * - PM2.5
     * - PM10
     * - Temperature (optional)
     * - Humidity (optional)
     */
    public List<CityEnvironmentData> parseCSV(String filePath) {
        List<CityEnvironmentData> records = new ArrayList<>();
        
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log.warn("CSV file not found at: {}", filePath);
                return records;
            }

            try (FileReader reader = new FileReader(file);
                 CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                         .setHeader()
                         .setSkipHeaderRecord(true)
                         .setIgnoreHeaderCase(true)
                         .setTrim(true)
                         .build())) {

                for (CSVRecord record : csvParser) {
                    try {
                        CityEnvironmentData data = parseSingleRecord(record);
                        if (data != null && data.isValid()) {
                            records.add(data);
                        } else {
                            log.debug("Skipping invalid row: {}", record);
                        }
                    } catch (Exception e) {
                        log.debug("Error parsing record: {}", record, e);
                    }
                }
            }

            log.info("Successfully parsed {} records from CSV", records.size());

        } catch (IOException e) {
            log.error("Error reading CSV file: {}", filePath, e);
        }

        return records;
    }

    /**
     * Parse a single CSV record into CityEnvironmentData.
     * Handles flexible column naming and missing values.
     */
    private CityEnvironmentData parseSingleRecord(CSVRecord record) {
        try {
            String city = getStringValue(record, "City", "city", "Location");
            if (city == null || city.trim().isEmpty()) {
                return null;
            }

            city = normalizeCityName(city);

            LocalDate date = getDateValue(record, "Date", "date");
            if (date == null) {
                return null;
            }

            Double aqi = getDoubleValue(record, "AQI", "aqi");
            Double pm25 = getDoubleValue(record, "PM2.5", "pm25", "PM25");
            Double pm10 = getDoubleValue(record, "PM10", "pm10");

            if (aqi == null || pm25 == null || pm10 == null) {
                return null;
            }

            Double temperature = getDoubleValue(record, "Temperature", "temperature", "Temp");
            Double humidity = getDoubleValue(record, "Humidity", "humidity");

            return CityEnvironmentData.builder()
                    .city(city)
                    .date(date)
                    .aqi(aqi)
                    .pm25(pm25)
                    .pm10(pm10)
                    .temperature(temperature)
                    .humidity(humidity)
                    .build();

        } catch (Exception e) {
            log.debug("Error parsing individual record", e);
            return null;
        }
    }

    /**
     * Gets string value from record, trying multiple column names.
     */
    private String getStringValue(CSVRecord record, String... columnNames) {
        for (String colName : columnNames) {
            if (record.isMapped(colName)) {
                String value = record.get(colName);
                if (value != null && !value.trim().isEmpty() && !value.equalsIgnoreCase("NA")) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    /**
     * Gets double value from record, trying multiple column names.
     */
    private Double getDoubleValue(CSVRecord record, String... columnNames) {
        for (String colName : columnNames) {
            if (record.isMapped(colName)) {
                String value = record.get(colName);
                if (value != null && !value.trim().isEmpty() && !value.equalsIgnoreCase("NA")) {
                    try {
                        return Double.parseDouble(value.trim());
                    } catch (NumberFormatException e) {
                        log.debug("Could not parse {} as double: {}", colName, value);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Gets date value from record, trying multiple column names and formats.
     */
    private LocalDate getDateValue(CSVRecord record, String... columnNames) {
        for (String colName : columnNames) {
            if (record.isMapped(colName)) {
                String value = record.get(colName);
                if (value != null && !value.trim().isEmpty()) {
                    try {
                        return LocalDate.parse(value.trim(), DATE_FORMATTER);
                    } catch (Exception e) {
                        log.debug("Could not parse {} as date: {}", colName, value);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Normalize city names for consistent querying.
     */
    private String normalizeCityName(String city) {
        String base = city.trim().split(",")[0].trim();
        String normalized = base.toLowerCase().replaceAll("\\s+", " ");
        return normalized;
    }

    /**
     * Convert a normalized city name into Title Case for display.
     */
    public String toTitleCase(String normalizedCity) {
        if (normalizedCity == null || normalizedCity.isBlank()) return "";
        if (CITY_TITLE_EXCEPTIONS.stream().anyMatch(e -> e.equalsIgnoreCase(normalizedCity))) {
            return CITY_TITLE_EXCEPTIONS.stream()
                    .filter(e -> e.equalsIgnoreCase(normalizedCity))
                    .findFirst()
                    .orElse(normalizedCity);
        }
        String[] parts = normalizedCity.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)))
              .append(p.length() > 1 ? p.substring(1) : "")
              .append(" ");
        }
        return sb.toString().trim();
    }

}
