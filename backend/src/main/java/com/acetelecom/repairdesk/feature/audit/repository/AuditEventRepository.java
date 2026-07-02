package com.acetelecom.repairdesk.feature.audit.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.audit.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, UUID entityId);
}
