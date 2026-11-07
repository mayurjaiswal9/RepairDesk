package com.acetelecom.repairdesk.feature.location.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.acetelecom.repairdesk.feature.location.domain.TechnicianLocationLog;

public interface TechnicianLocationLogRepository extends JpaRepository<TechnicianLocationLog, java.util.UUID> {

    List<TechnicianLocationLog> findByRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
            Instant from, Instant to);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM TechnicianLocationLog l WHERE l.recordedAt < :cutoff")
    int deleteRecordedBefore(@Param("cutoff") Instant cutoff);
}
