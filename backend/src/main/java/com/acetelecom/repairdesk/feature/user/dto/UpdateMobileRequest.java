package com.acetelecom.repairdesk.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMobileRequest(
        @NotBlank @Size(min = 7, max = 20)
        @Pattern(regexp = "^[+\\d][\\d\\s\\-]{6,19}$", message = "Invalid mobile number format")
        String mobile) {
}
