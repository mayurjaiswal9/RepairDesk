package com.acetelecom.repairdesk.feature.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerPinRequest(@NotBlank @Size(max = 2000) String text) {
}
