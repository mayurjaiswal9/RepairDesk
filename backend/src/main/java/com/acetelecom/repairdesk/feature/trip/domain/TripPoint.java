package com.acetelecom.repairdesk.feature.trip.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.common.domain.TenantEntity;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One GPS reading uploaded during a trip. {@code clientPointId} is unique per trip. */
@Entity
@Table(name = "trip_points")
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class TripPoint extends TenantEntity {

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "client_point_id", nullable = false, length = 64)
    private String clientPointId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "accuracy_m")
    private Double accuracyM;

    @Column(name = "speed_mps")
    private Double speedMps;

    @Column(nullable = false)
    private boolean mocked;
}
