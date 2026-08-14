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
        // Self-activate is refused (no recovery path mid-session); self-deactivate is allowed
        // so an admin can suspend themselves and be signed out on the next request.
        if (user.getId().equals(actor.id()) && newStatus == AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Cannot change your own account status");
        }
        if (newStatus == AccountStatus.DISABLED && user.getRole() == Role.ADMIN
                && user.getStatus() == AccountStatus.ACTIVE) {
            long otherActiveAdmins = userRepo.findActiveByRole(Role.ADMIN).stream()
                    .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                    .filter(u -> !u.getId().equals(userId))
                    .count();
            if (otherActiveAdmins == 0) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Cannot disable the last active admin");
            }
        }
        user.setStatus(newStatus);
        user.setUpdatedAt(Instant.now());
        return UserProfile.from(userRepo.save(user));
    }

    // ── STAFF-003: Edit user ──────────────────────────────────────────────

    public UserProfile updateUser(UUID userId, String name, String mobile, String roleName,
            AuthenticatedUser actor) {
        User user = requireUser(userId);
        if (name != null && !name.isBlank()) {
            user.setName(name.trim());
        }
        if (mobile != null && !mobile.isBlank() && !mobile.equals(user.getMobile())) {
            userRepo.findActiveByMobile(mobile).ifPresent(u -> {
                if (!u.getId().equals(userId)) {
                    throw new ApiException(ErrorCode.CONFLICT, "Mobile already registered");
                }
            });
            user.setMobile(mobile.trim());
        }
        if (roleName != null) {
            Role newRole;
            try {
                newRole = Role.valueOf(roleName.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid role: " + roleName);
            }
            if (newRole == Role.CUSTOMER) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Cannot assign CUSTOMER role via admin");
            }
            if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN
                    && user.getStatus() == AccountStatus.ACTIVE) {
                long otherActiveAdmins = userRepo.findActiveByRole(Role.ADMIN).stream()
                        .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                        .filter(u -> !u.getId().equals(userId))
                        .count();
                if (otherActiveAdmins == 0) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "Cannot demote the last active admin");
                }
            }
            user.setRole(newRole);
        }
        user.setUpdatedAt(Instant.now());
        return UserProfile.from(userRepo.save(user));
    }

    // ── STAFF-005: Admin reset password ──────────────────────────────────

    public void adminResetPassword(UUID userId, String newPassword, AuthenticatedUser actor) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Password must be at least 8 characters");
        }
        User user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        user.setUpdatedAt(Instant.now());
        userRepo.save(user);
    }

    // ── STAFF-004: Soft delete ────────────────────────────────────────────

    public void deleteUser(UUID userId, AuthenticatedUser actor) {
        User user = requireUser(userId);
        if (user.getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Cannot delete your own account");
        }
        if (user.getRole() == Role.ADMIN && user.getStatus() == AccountStatus.ACTIVE) {
            long otherActiveAdmins = userRepo.findActiveByRole(Role.ADMIN).stream()
                    .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                    .filter(u -> !u.getId().equals(userId))
                    .count();
            if (otherActiveAdmins == 0) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Cannot delete the last active admin");
            }
        }
        user.setStatus(AccountStatus.DELETED);
        user.setUpdatedAt(Instant.now());
        userRepo.save(user);
    }

    // ─────────────────────────────────────────────────────────────────────

    private User requireUser(UUID id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    }
}
