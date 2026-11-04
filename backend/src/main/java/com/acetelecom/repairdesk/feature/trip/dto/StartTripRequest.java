package com.acetelecom.repairdesk.feature.trip.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** Optional start fix. The body itself may be absent. */
public record StartTripRequest(
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("0.0") Double accuracyM,
        Boolean mocked) {
}
