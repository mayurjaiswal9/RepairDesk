package com.acetelecom.repairdesk.feature.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlaceSuggestRequest(
        @NotBlank @Size(min = 5, max = 200) String input,
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String sessionToken,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {
}
