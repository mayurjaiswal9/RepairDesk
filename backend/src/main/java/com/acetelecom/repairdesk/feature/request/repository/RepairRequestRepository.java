package com.acetelecom.repairdesk.feature.request.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {

    /** Customer's own requests — most recently updated first. */
    Page<RepairRequest> findAllByCustomerIdOrderByUpdatedAtDescCreatedAtDesc(UUID customerId, Pageable pageable);

    /**
     * Admin review queue. Optional status / category / free-text {@code q}.
     * Sort comes from {@link Pageable} (default submittedAt,desc from the client).
     */
    @Query("""
            SELECT r FROM RepairRequest r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:categoryId IS NULL OR r.deviceCategory.id = :categoryId)
              AND (
                :q IS NULL OR :q = ''
                OR LOWER(r.referenceNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(r.contactName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(r.contactMobile, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(r.customer.username) LIKE LOWER(CONCAT('%', :q, '%'))
              )
              AND (:hasFrom = false OR r.submittedAt >= :fromInclusive)
              AND (:hasTo = false OR r.submittedAt < :toExclusive)
            """)
    Page<RepairRequest> findQueueFiltered(
            @Param("status") RequestStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("q") String q,
            @Param("hasFrom") boolean hasFrom,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("hasTo") boolean hasTo,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable);

    Optional<RepairRequest> findByReferenceNumber(String referenceNumber);

    @Query("SELECT COUNT(r) FROM RepairRequest r WHERE r.status = :status")
    long countByStatus(@Param("status") RequestStatus status);

    java.util.List<RepairRequest> findByCustomerIdOrderByUpdatedAtDesc(UUID customerId);
}
