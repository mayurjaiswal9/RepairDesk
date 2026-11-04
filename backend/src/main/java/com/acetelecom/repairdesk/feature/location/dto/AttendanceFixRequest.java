package com.acetelecom.repairdesk.feature.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** Optional GPS reading sent with start day or end day. All fields may be absent. */
public record AttendanceFixRequest(
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("0.0") Double accuracyM,
        Boolean mocked) {
}
