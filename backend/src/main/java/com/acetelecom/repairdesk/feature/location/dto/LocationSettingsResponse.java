package com.acetelecom.repairdesk.feature.location.dto;

import java.time.LocalTime;

/** Full location settings, including allowance rates. Admin only. */
public record LocationSettingsResponse(
        Double shopLatitude,
        Double shopLongitude,
        int geofenceRadiusM,
        boolean checkInEnabled,
        boolean attendanceEnabled,
        boolean trackingEnabled,
        boolean travelEnabled,
        boolean etaEnabled,
        int pingIntervalMinutes,
        int pingDistanceM,
        LocalTime dayAutoEndTime,
        boolean allowStartOutsideGeofence,
        int farFromAddressM,
        int rateTwoWheelerPaise,
        int rateFourWheelerPaise,
        int retentionDays,
        String privacyNoticeVersion,
        boolean strictTripMode,
        boolean tripOpenMaps,
        int travelPingSeconds,
        int onSitePingMinutes) {
}
