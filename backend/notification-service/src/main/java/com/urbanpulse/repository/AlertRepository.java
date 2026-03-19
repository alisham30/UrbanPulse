package com.urbanpulse.repository;

import com.urbanpulse.entity.AlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<AlertEntity, String> {

    List<AlertEntity> findByCityIgnoreCaseOrderByRecordedAtDesc(String city);

    List<AlertEntity> findAllByOrderByRecordedAtDesc();

    @Query("SELECT a FROM AlertEntity a WHERE a.riskLevel IN :levels ORDER BY a.recordedAt DESC")
    List<AlertEntity> findByRiskLevels(@Param("levels") List<String> levels);

    @Modifying
    @Query("DELETE FROM AlertEntity a WHERE a.recordedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
