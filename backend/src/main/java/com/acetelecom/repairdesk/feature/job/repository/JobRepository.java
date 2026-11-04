package com.acetelecom.repairdesk.feature.job.repository;

import java.time.Instant;
import java.util.Collection;
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

    /** Staff/Admin: all jobs, optional single-status filter (legacy). */
    @Query("""
            SELECT j FROM Job j
            WHERE (:status IS NULL OR j.status = :status)
            """)
    Page<Job> findAll(@Param("status") JobStatus status, Pageable pageable);

    /** Staff/Admin: multi-status filter (dashboard deep links / APB-008). */
    @Query("""
            SELECT j FROM Job j
            WHERE j.status IN :statuses
            """)
    Page<Job> findByStatusIn(@Param("statuses") Collection<JobStatus> statuses, Pageable pageable);

    /**
     * Filtered job board. Sort comes from {@link Pageable}.
     * When {@code hasStatuses} is false, {@code statuses} is ignored (pass a non-empty dummy).
     * {@code unassigned=true} means no technician; otherwise optional {@code technicianId}.
     */
    @Query("""
            SELECT j FROM Job j
            WHERE (:hasStatuses = false OR j.status IN :statuses)
              AND (:technicianId IS NULL OR j.assignedTechnician.id = :technicianId)
              AND (:unassigned = false OR j.assignedTechnician IS NULL)
              AND (
                :q IS NULL OR :q = ''
                OR LOWER(j.referenceNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(j.contactName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(j.customer.username) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(j.deviceCategory.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(j.deviceBrand, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(j.deviceModel, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
              AND (:hasFrom = false OR j.createdAt >= :fromInclusive)
              AND (:hasTo = false OR j.createdAt < :toExclusive)
            """)
    Page<Job> findFiltered(
            @Param("hasStatuses") boolean hasStatuses,
            @Param("statuses") Collection<JobStatus> statuses,
            @Param("technicianId") UUID technicianId,
            @Param("unassigned") boolean unassigned,
            @Param("q") String q,
            @Param("hasFrom") boolean hasFrom,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("hasTo") boolean hasTo,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable);

    /** Technician: only assigned jobs. */
    Page<Job> findByAssignedTechnicianIdOrderByUpdatedAtDesc(UUID technicianId, Pageable pageable);

    /**
     * Technician board — always requires an assigned technician match (no
     * nullable {@code OR} pattern that some JPA providers evaluate poorly).
     */
    @Query("""
            SELECT j FROM Job j
            WHERE j.assignedTechnician.id = :technicianId
              AND (:hasStatuses = false OR j.status IN :statuses)
              AND (
                :q IS NULL OR :q = ''
                OR LOWER(j.referenceNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(j.contactName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(j.customer.username) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(j.deviceCategory.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(j.deviceBrand, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(j.deviceModel, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
              AND (:hasFrom = false OR j.createdAt >= :fromInclusive)
              AND (:hasTo = false OR j.createdAt < :toExclusive)
            """)
    Page<Job> findForTechnician(
            @Param("technicianId") UUID technicianId,
            @Param("hasStatuses") boolean hasStatuses,
            @Param("statuses") Collection<JobStatus> statuses,
            @Param("q") String q,
            @Param("hasFrom") boolean hasFrom,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("hasTo") boolean hasTo,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable);

    /** Still open, created before the start of today. Technician id null means the whole board. */
    @Query("""
            SELECT j.createdAt FROM Job j
            WHERE j.status NOT IN (
                com.acetelecom.repairdesk.feature.job.domain.JobStatus.COMPLETED,
                com.acetelecom.repairdesk.feature.job.domain.JobStatus.CANCELLED)
              AND j.createdAt < :startOfToday
              AND (:technicianId IS NULL OR j.assignedTechnician.id = :technicianId)
            """)
    List<Instant> findOpenCreatedBefore(
            @Param("startOfToday") Instant startOfToday,
            @Param("technicianId") UUID technicianId);

    /** Customer: jobs linked to their requests. */
    List<Job> findByCustomerId(UUID customerId);

    /** Dashboard counters. */
    long countByStatus(JobStatus status);

    /** Open jobs that already have a service pin, with the assigned technician loaded. */
    @Query("""
            SELECT j FROM Job j
            LEFT JOIN FETCH j.assignedTechnician
            WHERE j.status NOT IN (
                com.acetelecom.repairdesk.feature.job.domain.JobStatus.COMPLETED,
                com.acetelecom.repairdesk.feature.job.domain.JobStatus.CANCELLED)
              AND j.serviceLocation.latitude IS NOT NULL
              AND j.serviceLocation.longitude IS NOT NULL
            """)
    List<Job> findOpenWithServicePin();

    /** Other pinned, non-cancelled jobs of one technician changed since {@code since}. */
    @Query("""
            SELECT j FROM Job j
            WHERE j.assignedTechnician.id = :technicianId
              AND j.id <> :excludeJobId
              AND j.status <> com.acetelecom.repairdesk.feature.job.domain.JobStatus.CANCELLED
              AND j.serviceLocation.latitude IS NOT NULL
              AND j.serviceLocation.longitude IS NOT NULL
              AND j.updatedAt >= :since
            """)
    List<Job> findPinnedForTechnicianSince(
            @Param("technicianId") UUID technicianId,
            @Param("excludeJobId") UUID excludeJobId,
            @Param("since") Instant since);
}
