package com.acetelecom.repairdesk.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMobileRequest(

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 \\-]{8,15}$", message = "must be 8-15 digits, optional leading + or spaces/dashes")
        String mobile) {
}
