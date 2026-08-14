package com.acetelecom.repairdesk.feature.notification.domain;

import java.time.Instant;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /** The type of notification, e.g. REQUEST_SUBMITTED, JOB_APPROVED, etc. */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    /** Reference to the related entity (request/job reference number). */
    @Column(name = "reference_number", length = 30)
    private String referenceNumber;

    /** REPAIR_REQUEST | JOB | APPOINTMENT — see notifications_related_type_check. */
    @Column(name = "related_type", length = 20)
    private String relatedType;

    @Column(name = "related_id")
    private UUID relatedEntityId;

    @Column(name = "read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
