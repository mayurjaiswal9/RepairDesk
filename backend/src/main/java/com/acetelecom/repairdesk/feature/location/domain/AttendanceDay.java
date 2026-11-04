package com.acetelecom.repairdesk.feature.location.domain;

import java.time.Instant;
import java.time.LocalDate;
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

/** One technician work day on one India date. */
@Entity
@Table(name = "attendance_days")
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class AttendanceDay extends TenantEntity {

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_flag", nullable = false, length = 20)
    private LocationFlag startFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_flag", length = 20)
    private LocationFlag endFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private EndReason endReason;

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lng")
    private Double startLng;

    @Column(name = "start_accuracy_m")
    private Double startAccuracyM;

    @Column(name = "end_lat")
    private Double endLat;

    @Column(name = "end_lng")
    private Double endLng;

    @Column(name = "end_accuracy_m")
    private Double endAccuracyM;

    @Column(name = "privacy_notice_version", nullable = false, length = 20)
    private String privacyNoticeVersion;
}
