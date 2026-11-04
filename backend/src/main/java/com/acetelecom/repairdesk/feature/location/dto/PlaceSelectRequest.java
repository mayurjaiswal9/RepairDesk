package com.acetelecom.repairdesk.feature.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PlaceSelectRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{8,250}$") String placeId,
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String sessionToken) {
}
