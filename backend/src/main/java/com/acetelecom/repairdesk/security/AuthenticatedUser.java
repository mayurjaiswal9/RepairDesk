package com.acetelecom.repairdesk.security;

import java.util.UUID;

/**
 * Immutable principal stored in the {@code SecurityContext} after JWT validation.
 * Services obtain this via {@link CurrentUser}.
 *
 * @param companyId company that owns this account. Null only in unit tests that
 *                  build a principal without a company.
 */
public record AuthenticatedUser(UUID id, String username, String role, UUID companyId) {

    public AuthenticatedUser(UUID id, String username, String role) {
        this(id, username, role, null);
    }
}
