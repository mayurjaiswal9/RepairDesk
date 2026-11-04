package com.acetelecom.repairdesk.feature.trip.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A batch of queued points. Resending a {@code clientPointId} is harmless. */
public record TripPointsRequest(
        @NotNull @Size(max = 500) @Valid List<Point> points) {

    public record Point(
            @NotBlank @Size(max = 64) String clientPointId,
            @NotNull Instant recordedAt,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @DecimalMin("0.0") Double accuracyM,
            @DecimalMin("0.0") Double speedMps,
            Boolean mocked) {
    }
}
