package com.acetelecom.repairdesk.security;

import java.util.UUID;

/**
 * Immutable principal stored in the {@code SecurityContext} after JWT validation.
 * Services obtain this via {@link CurrentUser}.
 */
public record AuthenticatedUser(UUID id, String username, String role) {
}
