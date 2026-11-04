package com.acetelecom.repairdesk.feature.trip.domain;

import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.common.domain.TenantEntity;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A technician started a job in strict mode without a trip. No km is paid for it. */
@Entity
@Table(name = "job_no_trip_reasons")
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class JobNoTripReason extends TenantEntity {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30)
    private NoTripReason reasonCode;

    @Column(name = "reason_text", length = 500)
    private String reasonText;
}
