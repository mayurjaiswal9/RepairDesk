package com.acetelecom.repairdesk.feature.trip.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdjustDistanceRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") @Digits(integer = 4, fraction = 1) BigDecimal distanceKm,
        @NotBlank @Size(min = 3, max = 500) String reason) {
}
