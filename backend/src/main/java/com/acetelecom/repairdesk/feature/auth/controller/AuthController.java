package com.acetelecom.repairdesk.feature.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.auth.dto.AuthResponse;
import com.acetelecom.repairdesk.feature.auth.dto.ChangePasswordRequest;
import com.acetelecom.repairdesk.feature.auth.dto.LoginRequest;
import com.acetelecom.repairdesk.feature.auth.dto.RegisterRequest;
import com.acetelecom.repairdesk.feature.auth.dto.ResetPasswordRequest;
import com.acetelecom.repairdesk.feature.auth.dto.UpdateMobileRequest;
import com.acetelecom.repairdesk.feature.auth.dto.UserProfileResponse;
import com.acetelecom.repairdesk.feature.auth.service.AuthService;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * Authentication endpoints (public) and self-profile endpoints (authenticated).
 * Base path: {@code /api/v1/auth} and {@code /api/v1/me}.
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    // -------------------------------------------------------------------------
    // Public auth endpoints — /auth/**
    // -------------------------------------------------------------------------

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        // Stateless JWT: no server-side token storage to clear.
        // Client discards the token. Audit hook added in AUDIT-001 task.
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        // Enumeration-safe: always 204 regardless of whether the account exists.
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Authenticated self-profile — /me/**
    // -------------------------------------------------------------------------

    @GetMapping("/me")
    public UserProfileResponse getProfile() {
        return authService.getProfile(currentUser.get().id());
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMobile(@Valid @RequestBody UpdateMobileRequest req) {
        return authService.updateMobile(currentUser.get().id(), req);
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(currentUser.get().id(), req);
        return ResponseEntity.noContent().build();
    }
}
