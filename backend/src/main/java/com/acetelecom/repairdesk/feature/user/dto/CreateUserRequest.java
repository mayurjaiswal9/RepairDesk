package com.acetelecom.repairdesk.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(min = 7, max = 20)
        @Pattern(regexp = "^[+\\d][\\d\\s\\-]{6,19}$", message = "Invalid mobile number format")
        String mobile,
        @NotBlank @Size(min = 8, max = 72) String password,

        /** STAFF, TECHNICIAN, or ADMIN */
        @NotBlank String role) {
}
