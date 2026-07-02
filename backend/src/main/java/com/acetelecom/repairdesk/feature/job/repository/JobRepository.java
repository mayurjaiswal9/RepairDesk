package com.acetelecom.repairdesk.feature.job.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByReferenceNumber(String referenceNumber);

    Optional<Job> findByRequestId(UUID requestId);

    /** Staff/Admin: all jobs, optional status filter. */
    @Query("""
            SELECT j FROM Job j
            WHERE (:status IS NULL OR j.status = :status)
            ORDER BY j.priority ASC, j.updatedAt DESC
            """)
    Page<Job> findAll(@Param("status") String status, Pageable pageable);

    /** Technician: only assigned jobs. */
    Page<Job> findByAssignedTechnicianIdOrderByUpdatedAtDesc(UUID technicianId, Pageable pageable);

    /** Customer: jobs linked to their requests. */
    List<Job> findByCustomerId(UUID customerId);

    /** Dashboard counters. */
    long countByStatus(JobStatus status);
}
