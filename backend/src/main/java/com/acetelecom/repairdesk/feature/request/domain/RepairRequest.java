package com.acetelecom.repairdesk.feature.request.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.common.domain.TenantEntity;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;
import com.acetelecom.repairdesk.feature.location.domain.ServiceLocation;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;
import com.acetelecom.repairdesk.feature.user.domain.User;

@Entity
@Table(name = "repair_requests")
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class RepairRequest extends TenantEntity {

    @Column(name = "reference_number", nullable = false, unique = true, length = 30)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_category_id", nullable = false)
    private DeviceCategory deviceCategory;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_mobile", nullable = false, length = 20)
    private String contactMobile;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "device_brand", length = 100)
    private String deviceBrand;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "device_serial", length = 100)
    private String deviceSerial;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "warranty_status", length = 20)
    private WarrantyStatus warrantyStatus;

    @Column(name = "issue_description", nullable = false, columnDefinition = "text")
    private String issueDescription;

    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @Column(name = "preferred_time", nullable = false)
    private LocalTime preferredTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestStatus status = RequestStatus.PENDING_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.NORMAL;

    @Column(name = "rejection_reason_code", length = 40)
    private String rejectionReasonCode;

    @Column(name = "rejection_reason_internal", columnDefinition = "text")
    private String rejectionReasonInternal;

    @Column(name = "customer_facing_message", columnDefinition = "text")
    private String customerFacingMessage;

    @Column(name = "info_requested_message", columnDefinition = "text")
    private String infoRequestedMessage;

    @Column(name = "customer_info_response", columnDefinition = "text")
    private String customerInfoResponse;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestOrigin origin = RequestOrigin.CUSTOMER_APP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "intake_channel", length = 20)
    private IntakeChannel intakeChannel;

    @Embedded
    private ServiceLocation serviceLocation = new ServiceLocation();

    public boolean isEditableByOwner() {
        return status == RequestStatus.PENDING_REVIEW
                || status == RequestStatus.INFORMATION_REQUIRED;
    }

    public boolean isCancellableByOwner() {
        return isEditableByOwner();
    }

    public boolean isOwnedBy(UUID userId) {
        return customer != null && customer.getId().equals(userId);
    }
}
