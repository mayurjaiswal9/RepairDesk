package com.acetelecom.repairdesk.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank
        String username,

        @NotBlank
        @Size(min = 8, max = 100, message = "must be at least 8 characters")
        String newPassword) {
}
