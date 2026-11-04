package com.acetelecom.repairdesk.feature.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GeocodeRequest(@NotBlank @Size(max = 400) String address) {
}
