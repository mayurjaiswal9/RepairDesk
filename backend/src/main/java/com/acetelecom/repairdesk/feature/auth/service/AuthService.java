package com.acetelecom.repairdesk.feature.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.auth.dto.AuthResponse;
import com.acetelecom.repairdesk.feature.auth.dto.ChangePasswordRequest;
import com.acetelecom.repairdesk.feature.auth.dto.LoginRequest;
import com.acetelecom.repairdesk.feature.auth.dto.RegisterRequest;
import com.acetelecom.repairdesk.feature.auth.dto.ResetPasswordRequest;
import com.acetelecom.repairdesk.feature.auth.dto.UpdateMobileRequest;
import com.acetelecom.repairdesk.feature.auth.dto.UserProfileResponse;
import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.JwtService;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** AUTH-010: Register a new CUSTOMER account. */
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.findActiveByUsername(req.username()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Username already in use");
        }
        if (userRepository.findActiveByMobile(req.mobile()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Mobile number already in use");
        }

        User user = new User();
        user.setUsername(req.username().strip());
        user.setMobile(req.mobile().replaceAll("[\\s\\-]", ""));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.CUSTOMER);
        user.setStatus(AccountStatus.ACTIVE);
        user = userRepository.save(user);

        return toAuthResponse(user);
    }

    /** AUTH-011: Authenticate and return a JWT. */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findActiveByUsername(req.username())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials"));

        if (user.getStatus() == AccountStatus.DISABLED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Account is disabled");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials");
        }

        return toAuthResponse(user);
    }

    /** AUTH-012: Reset password (enumeration-safe — same response regardless of account existence). */
    public void resetPassword(ResetPasswordRequest req) {
        userRepository.findActiveByUsername(req.username()).ifPresent(user -> {
            if (user.getStatus() == AccountStatus.DISABLED) {
                return;
            }
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
        });
    }

    /** AUTH-013: Return own profile. */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = requireUser(userId);
        return UserProfileResponse.from(user);
    }

    /** AUTH-013: Update own mobile number. */
    public UserProfileResponse updateMobile(UUID userId, UpdateMobileRequest req) {
        User user = requireUser(userId);
        String normalized = req.mobile().replaceAll("[\\s\\-]", "");
        userRepository.findActiveByMobile(normalized)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.CONFLICT, "Mobile number already in use");
                });
        user.setMobile(normalized);
        user.setUpdatedAt(Instant.now());
        return UserProfileResponse.from(userRepository.save(user));
    }

    /** AUTH-013: Change own password (requires current password). */
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() != AccountStatus.DELETED)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                jwtService.mintToken(user),
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.isMustChangePassword());
    }
}
