package com.acetelecom.repairdesk.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;

/**
 * Accessor services use to read the authenticated user's identity and role from
 * the {@code SecurityContext} (AUTH-005).
 */
@Component
public class CurrentUser {

    public AuthenticatedUser get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    public AuthenticatedUser getOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            return principal;
        }
        return null;
    }
}
