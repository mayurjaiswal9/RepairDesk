package com.acetelecom.repairdesk.feature.audit.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.feature.company.CompanyAssignListener;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;
import com.acetelecom.repairdesk.feature.company.CompanyScoped;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "audit_events")
@EntityListeners(CompanyAssignListener.class)
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent implements CompanyScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_username", length = 60)
    private String actorUsername;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "reason", columnDefinition = "text")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
