package com.acetelecom.repairdesk.feature.location.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin update. Every field up to {@code privacyNoticeVersion} is required so a save cannot
 * silently drop a switch. The trip fields after it are optional: null keeps the stored value.
 */
public record UpdateLocationSettingsRequest(
        @DecimalMin("-90.0") @DecimalMax("90.0") Double shopLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double shopLongitude,
        @NotNull @Min(50) @Max(1000) Integer geofenceRadiusM,
        @NotNull Boolean checkInEnabled,
        @NotNull Boolean attendanceEnabled,
        @NotNull Boolean trackingEnabled,
        @NotNull Boolean travelEnabled,
        @NotNull Boolean etaEnabled,
        @NotNull @Min(1) @Max(30) Integer pingIntervalMinutes,
        @NotNull @Min(50) @Max(5000) Integer pingDistanceM,
        @NotNull LocalTime dayAutoEndTime,
        @NotNull Boolean allowStartOutsideGeofence,
        @NotNull @Min(50) @Max(5000) Integer farFromAddressM,
        @NotNull @Min(0) @Max(100_000) Integer rateTwoWheelerPaise,
        @NotNull @Min(0) @Max(100_000) Integer rateFourWheelerPaise,
        @NotNull @Min(7) @Max(365) Integer retentionDays,
        @NotBlank @Size(max = 20) String privacyNoticeVersion,
        Boolean strictTripMode,
        Boolean tripOpenMaps,
        @Min(30) @Max(600) Integer travelPingSeconds,
        @Min(15) @Max(240) Integer onSitePingMinutes) {
}
