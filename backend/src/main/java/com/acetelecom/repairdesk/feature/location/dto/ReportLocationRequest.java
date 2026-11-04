package com.acetelecom.repairdesk.feature.location.dto;

import java.util.UUID;

import com.acetelecom.repairdesk.feature.location.domain.PresenceState;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** A technician's latest position. {@code state} is optional. */
public record ReportLocationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("0.0") @DecimalMax("5000.0") Double accuracyM,
        UUID jobId,
        PresenceState state) {
}
