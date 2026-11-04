package com.acetelecom.repairdesk.feature.location.dto;

import java.time.LocalTime;

/** What the app needs to adapt. Allowance rates are not included. */
public record LocationConfigResponse(
        boolean checkInEnabled,
        boolean attendanceEnabled,
        boolean trackingEnabled,
        boolean travelEnabled,
        boolean etaEnabled,
        int pingIntervalMinutes,
        int pingDistanceM,
        LocalTime dayAutoEndTime,
        boolean allowStartOutsideGeofence,
        int geofenceRadiusM,
        Double shopLatitude,
        Double shopLongitude,
        int farFromAddressM,
        String privacyNoticeVersion,
        boolean strictTripMode,
        boolean tripOpenMaps,
        int travelPingSeconds,
        int onSitePingMinutes,
        boolean autoEndDay) {
}
