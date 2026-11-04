package com.acetelecom.repairdesk.feature.trip.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.acetelecom.repairdesk.feature.trip.domain.JobTrip;
import com.acetelecom.repairdesk.feature.trip.domain.TripStatus;

import jakarta.persistence.LockModeType;

public interface JobTripRepository extends JpaRepository<JobTrip, UUID> {

    /** Serialises point uploads for one trip so the duplicate check and totals stay consistent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM JobTrip t WHERE t.id = :id")
    Optional<JobTrip> findForUpdate(@Param("id") UUID id);

    Optional<JobTrip> findFirstByTechnicianIdAndStatusOrderByStartedAtDesc(UUID technicianId, TripStatus status);

    List<JobTrip> findByJobIdAndStatus(UUID jobId, TripStatus status);

    boolean existsByJobIdAndTechnicianIdAndStatus(UUID jobId, UUID technicianId, TripStatus status);

    List<JobTrip> findByJobIdOrderByStartedAtAsc(UUID jobId);

    List<JobTrip> findByStatusAndStartedAtBefore(TripStatus status, Instant cutoff);

    List<JobTrip> findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
            Instant from, Instant to);

    List<JobTrip> findByTechnicianIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
            UUID technicianId, Instant from, Instant to);
}
