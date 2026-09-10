package com.acetelecom.repairdesk.feature.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 100)
        String name,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 \\-]{8,15}$")
        String mobile,

        @Size(max = 50)
        String username,

        @Size(max = 100)
        String password) {
}
