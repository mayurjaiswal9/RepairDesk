package com.acetelecom.repairdesk.feature.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.dto.ChangePasswordRequest;
import com.acetelecom.repairdesk.feature.user.dto.CreateUserRequest;
import com.acetelecom.repairdesk.feature.user.dto.UpdateMobileRequest;
import com.acetelecom.repairdesk.feature.user.dto.UserProfile;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Self-profile (AUTH-013) ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserProfile getMe(UUID userId) {
        return UserProfile.from(requireUser(userId));
    }

    public UserProfile updateMobile(UUID userId, UpdateMobileRequest req) {
        User user = requireUser(userId);
        if (!user.getMobile().equals(req.mobile())) {
            userRepo.findActiveByMobile(req.mobile()).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ApiException(ErrorCode.CONFLICT, "Mobile number already in use");
                }
            });
        }
        user.setMobile(req.mobile());
        user.setUpdatedAt(Instant.now());
        return UserProfile.from(userRepo.save(user));
    }

    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        user.setUpdatedAt(Instant.now());
        userRepo.save(user);
    }

    // ── Staff / user management (STAFF-001) ──────────────────────────────

    @Transactional(readOnly = true)
    public List<UserProfile> listAll() {
        return userRepo.findAllNonDeleted().stream().map(UserProfile::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserProfile> listByRole(Role role) {
        return userRepo.findActiveByRole(role).stream().map(UserProfile::from).toList();
    }

    public UserProfile create(CreateUserRequest req, AuthenticatedUser actor) {
        Role role;
        try {
            role = Role.valueOf(req.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Invalid role: " + req.role());
        }
        if (role == Role.CUSTOMER) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Customers must self-register");
        }

        userRepo.findActiveByUsername(req.username()).ifPresent(u -> {
            throw new ApiException(ErrorCode.CONFLICT, "Username already taken");
        });
        userRepo.findActiveByMobile(req.mobile()).ifPresent(u -> {
            throw new ApiException(ErrorCode.CONFLICT, "Mobile already registered");
        });

        User user = new User();
        user.setUsername(req.username());
        user.setName(req.name());
        user.setMobile(req.mobile());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(true);
        return UserProfile.from(userRepo.save(user));
    }

    public UserProfile setStatus(UUID userId, AccountStatus newStatus, AuthenticatedUser actor) {
        User user = requireUser(userId);
        if (user.getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Cannot change your own account status");
        }
        user.setStatus(newStatus);
        user.setUpdatedAt(Instant.now());
        return UserProfile.from(userRepo.save(user));
    }

    // ─────────────────────────────────────────────────────────────────────

    private User requireUser(UUID id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    }
}
