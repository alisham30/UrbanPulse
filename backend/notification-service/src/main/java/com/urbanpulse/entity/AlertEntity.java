package com.urbanpulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_city", columnList = "city, recordedAt"),
        @Index(name = "idx_alert_risk", columnList = "riskLevel, recordedAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertEntity {

    @Id
    @Column(length = 64)
    private String alertId;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(length = 50)
    private String riskLevel;

    @Column(length = 1000)
    private String message;

    private Integer aqi;
    private Double cityHealthScore;

    @Column(length = 50)
    private String primaryDriver;

    @Column(length = 50)
    private String alertType;

    @Column(length = 50)
    private String alertState;
}
