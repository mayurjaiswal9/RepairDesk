package com.acetelecom.repairdesk.feature.location.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.feature.company.CompanyAssignListener;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;
import com.acetelecom.repairdesk.feature.company.CompanyScoped;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Location switches for one company. The ACE row stays id 1 until a later company is added. */
@Entity
@Table(name = "location_settings")
@EntityListeners(CompanyAssignListener.class)
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class LocationSettings implements CompanyScoped {

    @Id
    private Short id = 1;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "check_in_enabled", nullable = false)
    private boolean checkInEnabled = false;

    @Column(name = "attendance_enabled", nullable = false)
    private boolean attendanceEnabled = false;

    @Column(name = "tracking_enabled", nullable = false)
    private boolean trackingEnabled = false;

    @Column(name = "travel_enabled", nullable = false)
    private boolean travelEnabled = false;

    @Column(name = "eta_enabled", nullable = false)
    private boolean etaEnabled = false;

    @Column(name = "ping_interval_minutes", nullable = false)
    private int pingIntervalMinutes = 5;

    @Column(name = "ping_distance_m", nullable = false)
    private int pingDistanceM = 200;

    @Column(name = "day_auto_end_time", nullable = false)
    private LocalTime dayAutoEndTime = LocalTime.of(19, 0);

    @Column(name = "allow_start_outside_geofence", nullable = false)
    private boolean allowStartOutsideGeofence = false;

    @Column(name = "far_from_address_m", nullable = false)
    private int farFromAddressM = 300;

    @Column(name = "rate_two_wheeler_paise", nullable = false)
    private int rateTwoWheelerPaise = 500;

    @Column(name = "rate_four_wheeler_paise", nullable = false)
    private int rateFourWheelerPaise = 1000;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays = 90;

    @Column(name = "privacy_notice_version", nullable = false, length = 20)
    private String privacyNoticeVersion = "1";

    @Column(name = "strict_trip_mode", nullable = false)
    private boolean strictTripMode = true;

    @Column(name = "trip_open_maps", nullable = false)
    private boolean tripOpenMaps = true;

    @Column(name = "travel_ping_seconds", nullable = false)
    private int travelPingSeconds = 120;

    @Column(name = "on_site_ping_minutes", nullable = false)
    private int onSitePingMinutes = 60;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private long version;
}
