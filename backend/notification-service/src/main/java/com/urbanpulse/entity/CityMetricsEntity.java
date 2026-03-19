package com.urbanpulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "city_metrics", indexes = {
        @Index(name = "idx_city_timestamp", columnList = "city, recordedAt"),
        @Index(name = "idx_recorded_at", columnList = "recordedAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CityMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false)
    private Instant recordedAt;

    private Double latitude;
    private Double longitude;
    private Integer aqi;
    private Double pm25;
    private Double pm10;
    private Double temperature;
    private Integer humidity;
    private Integer pressure;
    private Double windSpeed;
    private Integer cloudPercentage;

    @Column(length = 255)
    private String weatherDescription;

    private Double baselineAqi;
    private Double aqiDeviationPercent;
    private Double cityHealthScore;
    private Boolean anomaly;

    @Column(length = 50)
    private String riskLevel;

    @Column(length = 1000)
    private String alertMessage;

    private Double rollingAqiAverage;

    @Column(length = 20)
    private String aqiTrend;

    @Column(length = 50)
    private String primaryDriver;

    @Column(length = 50)
    private String validationStatus;

    private Double dataConfidenceScore;
    private Double openAqPm25;
    private Double openAqPm10;

    @Column(length = 500)
    private String recommendation;

    private Double aqiTimeChangePct;
}
