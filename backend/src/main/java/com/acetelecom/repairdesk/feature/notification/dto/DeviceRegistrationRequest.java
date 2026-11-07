package com.acetelecom.repairdesk.feature.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceRegistrationRequest(
        @NotBlank @Size(max = 512) String token,
        @Size(max = 20) String platform) {
}
