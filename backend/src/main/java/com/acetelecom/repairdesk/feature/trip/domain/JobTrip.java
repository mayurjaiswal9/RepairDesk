package com.acetelecom.repairdesk.feature.trip.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.common.domain.TenantEntity;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;
import com.acetelecom.repairdesk.feature.location.domain.VehicleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One drive by a technician to a job. Totals stay after the points are deleted. */
@Entity
@Table(name = "job_trips")
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class JobTrip extends TenantEntity {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lng")
    private Double startLng;

    @Column(name = "start_accuracy_m")
    private Double startAccuracyM;

    @Column(name = "start_mocked", nullable = false)
    private boolean startMocked;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_kind", nullable = false, length = 20)
    private TripStartKind startKind = TripStartKind.UNKNOWN;

    @Column(name = "start_job_id")
    private UUID startJobId;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_lat")
    private Double endLat;

    @Column(name = "end_lng")
    private Double endLng;

    @Column(name = "end_accuracy_m")
    private Double endAccuracyM;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private TripEndReason endReason;

    @Column(name = "tracked_distance_m", nullable = false)
    private int trackedDistanceM;

    @Column(name = "estimated_gap_m", nullable = false)
    private int estimatedGapM;

    @Column(name = "planned_distance_m")
    private Integer plannedDistanceM;

    @Column(name = "planned_duration_s")
    private Integer plannedDurationS;

    @Column(name = "planned_estimated", nullable = false)
    private boolean plannedEstimated;

    @Column(name = "point_count", nullable = false)
    private int pointCount;

    @Column(name = "gap_count", nullable = false)
    private int gapCount;

    @Column(name = "mocked_points", nullable = false)
    private int mockedPoints;

    @Column(length = 200)
    private String flags;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 20)
    private VehicleType vehicleType;

    @Column(name = "rate_paise")
    private Integer ratePaise;

    @Column(name = "adjusted_distance_m")
    private Integer adjustedDistanceM;

    @Column(name = "adjust_reason", length = 500)
    private String adjustReason;

    @Column(name = "adjusted_by")
    private UUID adjustedBy;

    @Column(name = "adjusted_at")
    private Instant adjustedAt;

    public boolean hasStartFix() {
        return startLat != null && startLng != null;
    }

    public boolean hasEndFix() {
        return endLat != null && endLng != null;
    }

    /** Tracked plus estimated gaps, before any adjustment. */
    public int recordedDistanceM() {
        return trackedDistanceM + estimatedGapM;
    }

    public int payableDistanceM() {
        if (status == TripStatus.CANCELLED) {
            return 0;
        }
        if (adjustedDistanceM != null) {
            return adjustedDistanceM;
        }
        return recordedDistanceM();
    }

    public Integer amountPaise() {
        if (status == TripStatus.ACTIVE || ratePaise == null) {
            return null;
        }
        return (int) Math.round(payableDistanceM() * (double) ratePaise / 1000d);
    }

    public List<String> flagNames() {
        if (flags == null || flags.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String part : flags.split(",")) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        return names;
    }

    public void replaceFlags(Collection<TripFlag> values) {
        if (values == null || values.isEmpty()) {
            flags = null;
            return;
        }
        List<String> names = new ArrayList<>();
        for (TripFlag flag : values) {
            names.add(flag.name());
        }
        flags = String.join(",", names);
    }
}
