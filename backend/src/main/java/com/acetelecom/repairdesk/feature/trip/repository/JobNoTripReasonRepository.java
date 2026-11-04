package com.acetelecom.repairdesk.feature.trip.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.trip.domain.JobNoTripReason;

public interface JobNoTripReasonRepository extends JpaRepository<JobNoTripReason, UUID> {

    List<JobNoTripReason> findByJobIdOrderByCreatedAtDesc(UUID jobId);

    List<JobNoTripReason> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Instant from, Instant to);

    List<JobNoTripReason> findByTechnicianIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            UUID technicianId, Instant from, Instant to);
}
