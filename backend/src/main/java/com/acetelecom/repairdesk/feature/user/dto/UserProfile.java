package com.acetelecom.repairdesk.feature.user.dto;

import java.util.UUID;

import com.acetelecom.repairdesk.feature.user.domain.User;

public record UserProfile(
        UUID id,
        String username,
        String name,
        String mobile,
        String role,
        String status,
        boolean mustChangePassword) {

    public static UserProfile from(User u) {
        return new UserProfile(
                u.getId(),
                u.getUsername(),
                u.getName(),
                u.getMobile(),
                u.getRole().name(),
                u.getStatus().name(),
                u.isMustChangePassword());
    }
}
