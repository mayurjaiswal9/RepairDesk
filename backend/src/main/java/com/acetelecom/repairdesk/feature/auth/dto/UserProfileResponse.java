package com.acetelecom.repairdesk.feature.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.user.domain.User;

public record UserProfileResponse(
        UUID id,
        String username,
        String mobile,
        String name,
        String role,
        String status,
        boolean mustChangePassword,
        Instant createdAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getMobile(),
                user.getName(),
                user.getRole().name(),
                user.getStatus().name(),
                user.isMustChangePassword(),
                user.getCreatedAt()); // inherited from BaseEntity
    }
}
