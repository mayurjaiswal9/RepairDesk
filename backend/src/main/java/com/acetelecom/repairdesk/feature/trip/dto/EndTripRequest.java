package com.acetelecom.repairdesk.feature.trip.dto;

import com.acetelecom.repairdesk.feature.trip.domain.TripEndReason;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** {@code reason} is ARRIVED, GEOFENCE or CANCELLED. */
public record EndTripRequest(
        @NotNull TripEndReason reason,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("0.0") Double accuracyM) {
}
