package com.acetelecom.repairdesk.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank
        String username,

        @NotBlank
        String password,

        /** Blank or omitted means ACE Telecom. */
        String companyCode) {
}
