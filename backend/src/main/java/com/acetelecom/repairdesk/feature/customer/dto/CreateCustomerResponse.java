package com.acetelecom.repairdesk.feature.customer.dto;

import java.util.UUID;

import com.acetelecom.repairdesk.feature.user.domain.User;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateCustomerResponse(
        UUID id,
        String username,
        String name,
        String mobile,
        String role,
        String status,
        boolean mustChangePassword,
        String generatedPassword) {

    public static CreateCustomerResponse from(User user, String generatedPassword) {
        return new CreateCustomerResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getMobile(),
                user.getRole().name(),
                user.getStatus().name(),
                user.isMustChangePassword(),
                generatedPassword);
    }
}
