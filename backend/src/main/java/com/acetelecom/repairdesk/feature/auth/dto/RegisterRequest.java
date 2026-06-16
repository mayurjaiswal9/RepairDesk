package com.acetelecom.repairdesk.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 \\-]{8,15}$", message = "must be 8-15 digits, optional leading + or spaces/dashes")
        String mobile,

        @NotBlank
        @Size(min = 8, max = 100, message = "must be at least 8 characters")
        String password) {
}
