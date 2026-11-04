package com.acetelecom.repairdesk.feature.job.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.common.domain.TenantEntity;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;
import com.acetelecom.repairdesk.feature.location.domain.ServiceLocation;
import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;
import com.acetelecom.repairdesk.feature.request.domain.Priority;
import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "jobs")
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class Job extends TenantEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private RepairRequest request;

    @Column(name = "reference_number", nullable = false, unique = true, length = 30)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_technician_id")
    private User assignedTechnician;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_category_id", nullable = false)
    private DeviceCategory deviceCategory;

    // ── Snapshot fields (copied from request at approval time) ──
    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_mobile", length = 20)
    private String contactMobile;

    @Column(name = "device_brand", length = 100)
    private String deviceBrand;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "device_serial", length = 100)
    private String deviceSerial;

    @Column(name = "issue_description", columnDefinition = "text")
    private String issueDescription;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_confirmed", nullable = false)
    private boolean warrantyConfirmed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.NORMAL;

    @Column(name = "resolution_summary", columnDefinition = "text")
    private String resolutionSummary;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancelReason;

    // ── Active appointment pointer ──
    @Column(name = "current_appointment_id")
    private UUID currentAppointmentId;

    // ── Technician-visible fields only ──
    @Column(name = "internal_notes", columnDefinition = "text")
    private String internalNotes;

    @Embedded
    private ServiceLocation serviceLocation = new ServiceLocation();

    /** Convenience: is this job in a state where a technician can transition it? */
    public boolean canTechnicianTransition(UUID technicianId) {
        if (assignedTechnician == null || !assignedTechnician.getId().equals(technicianId)) {
            return false;
        }
        return status == JobStatus.ASSIGNED
                || status == JobStatus.SCHEDULED
                || status == JobStatus.IN_PROGRESS
                || status == JobStatus.AWAITING_PARTS;
    }
}
