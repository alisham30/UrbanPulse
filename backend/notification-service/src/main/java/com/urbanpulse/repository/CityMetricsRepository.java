package com.urbanpulse.repository;

import com.urbanpulse.entity.CityMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CityMetricsRepository extends JpaRepository<CityMetricsEntity, Long> {

    List<CityMetricsEntity> findByCityIgnoreCaseAndRecordedAtBetweenOrderByRecordedAtDesc(
            String city, Instant start, Instant end);

    List<CityMetricsEntity> findByCityIgnoreCaseOrderByRecordedAtDesc(String city);

    @Query("SELECT m FROM CityMetricsEntity m WHERE m.city = :city ORDER BY m.recordedAt DESC LIMIT :limit")
    List<CityMetricsEntity> findRecentByCity(@Param("city") String city, @Param("limit") int limit);

    @Query("SELECT DISTINCT m.city FROM CityMetricsEntity m")
    List<String> findDistinctCities();

    @Modifying
    @Query("DELETE FROM CityMetricsEntity m WHERE m.recordedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    long countByCity(String city);
}
