package com.acetelecom.repairdesk.feature.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String username,
        String role,
        boolean mustChangePassword) {
}
