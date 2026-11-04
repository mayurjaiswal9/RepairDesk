package com.acetelecom.repairdesk.feature.trip.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.acetelecom.repairdesk.feature.trip.domain.TripPoint;

public interface TripPointRepository extends JpaRepository<TripPoint, UUID> {

    List<TripPoint> findByTripIdOrderByRecordedAtAsc(UUID tripId);

    long countByTripId(UUID tripId);

    @Query("SELECT p.clientPointId FROM TripPoint p WHERE p.tripId = :tripId AND p.clientPointId IN :ids")
    List<String> findExistingClientPointIds(@Param("tripId") UUID tripId, @Param("ids") Collection<String> ids);

    @Modifying
    @Query("DELETE FROM TripPoint p WHERE p.companyId = :companyId AND p.recordedAt < :cutoff")
    int deleteRecordedBefore(@Param("companyId") UUID companyId, @Param("cutoff") Instant cutoff);
}
