package com.acetelecom.repairdesk.feature.audit.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.feature.audit.domain.AuditEvent;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.audit.repository.AuditEventRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

/**
 * Append-only writer for the audit log (AUDIT-001).
 * Uses REQUIRES_NEW so audit writes are committed even if the outer
 * transaction rolls back (preserves the audit trail for failed attempts).
 */
@Service
public class AuditService {

    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuthenticatedUser actor, String entityType, UUID entityId,
            String action, String detail) {
        AuditEvent event = new AuditEvent();
        if (actor != null) {
            event.setActorId(actor.id());
            event.setActorUsername(actor.username());
            event.setCompanyId(actor.companyId());
        }
        if (event.getCompanyId() == null) {
            event.setCompanyId(CompanyContext.get());
        }
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setAction(action);
        event.setDetail(detail);
        repo.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> history(String entityType, UUID entityId) {
        return repo.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId);
    }
}
