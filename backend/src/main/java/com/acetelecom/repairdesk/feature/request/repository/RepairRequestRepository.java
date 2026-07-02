package com.acetelecom.repairdesk.feature.request.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {

    /** Customer's own requests — most recently updated first. */
    Page<RepairRequest> findAllByCustomerIdOrderByUpdatedAtDescCreatedAtDesc(UUID customerId, Pageable pageable);

    /** Staff/Admin queue — filtered by optional status, ordered by priority then submit date. */
    @Query("""
            SELECT r FROM RepairRequest r
            WHERE (:status IS NULL OR r.status = :status)
            ORDER BY r.priority ASC, r.submittedAt ASC
            """)
    Page<RepairRequest> findQueueFiltered(@Param("status") String status, Pageable pageable);

    Optional<RepairRequest> findByReferenceNumber(String referenceNumber);

    @Query("SELECT COUNT(r) FROM RepairRequest r WHERE r.status = :status")
    long countByStatus(@Param("status") String status);

    java.util.List<RepairRequest> findByCustomerIdOrderByUpdatedAtDesc(UUID customerId);
}
