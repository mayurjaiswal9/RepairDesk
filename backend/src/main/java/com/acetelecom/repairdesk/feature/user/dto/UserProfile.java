package com.acetelecom.repairdesk.feature.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.user.domain.User;

public record UserProfile(
        UUID id,
        String username,
        String name,
        String mobile,
        String role,
        String status,
        boolean mustChangePassword,
        Instant createdAt,
        Integer repairCount) {

    public static UserProfile from(User u) {
        return from(u, null);
    }

    public static UserProfile from(User u, Integer repairCount) {
        return new UserProfile(
                u.getId(),
                u.getUsername(),
                u.getName(),
                u.getMobile(),
                u.getRole().name(),
                u.getStatus().name(),
                u.isMustChangePassword(),
                u.getCreatedAt(),
                repairCount);
    }
}
