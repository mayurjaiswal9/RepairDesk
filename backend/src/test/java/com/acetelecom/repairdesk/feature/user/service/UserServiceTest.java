package com.acetelecom.repairdesk.feature.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.dto.CreateUserRequest;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

/** TEST-005: Staff management unit tests. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepo;

    private PasswordEncoder passwordEncoder;
    private UserService userService;
    private AuthenticatedUser adminActor;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepo, passwordEncoder);
        adminActor = new AuthenticatedUser(UUID.randomUUID(), "admin", "ADMIN");
        CompanyContext.set(UUID.randomUUID());
    }

    @AfterEach
    void clearCompany() {
        CompanyContext.clear();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_staff_succeeds() {
        CreateUserRequest req = new CreateUserRequest(
                "newstaff", "Staff User", "9876543210", "Pass@1234", "STAFF");
        when(userRepo.findActiveByUsername("newstaff")).thenReturn(Optional.empty());
        when(userRepo.findActiveByMobile("9876543210")).thenReturn(Optional.empty());
        when(userRepo.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        var profile = userService.create(req, adminActor);

        assertThat(profile.username()).isEqualTo("newstaff");
        assertThat(profile.role()).isEqualTo("STAFF");
    }

    @Test
    void create_customerRole_throwsValidation() {
        CreateUserRequest req = new CreateUserRequest(
                "newcust", "Customer", "9000000000", "Pass@1234", "CUSTOMER");

        assertThatThrownBy(() -> userService.create(req, adminActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("self-register");
    }

    @Test
    void create_duplicateUsername_throwsConflict() {
        CreateUserRequest req = new CreateUserRequest(
                "existing", "Staff", "9000000001", "Pass@1234", "STAFF");
        when(userRepo.findActiveByUsername("existing"))
                .thenReturn(Optional.of(buildUser(UUID.randomUUID(), Role.STAFF)));

        assertThatThrownBy(() -> userService.create(req, adminActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Username");
    }

    @Test
    void create_duplicateMobile_throwsConflict() {
        CreateUserRequest req = new CreateUserRequest(
                "newuser", "User", "9000000002", "Pass@1234", "TECHNICIAN");
        when(userRepo.findActiveByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepo.findActiveByMobile("9000000002"))
                .thenReturn(Optional.of(buildUser(UUID.randomUUID(), Role.STAFF)));

        assertThatThrownBy(() -> userService.create(req, adminActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Mobile");
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Test
    void deleteUser_self_throwsValidation() {
        UUID actorId = adminActor.id();
        User user = buildUser(actorId, Role.ADMIN);
        when(userRepo.findById(actorId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deleteUser(actorId, adminActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own account");
    }

    @Test
    void deleteUser_lastAdmin_throwsConflict() {
        UUID userId = UUID.randomUUID();
        User adminUser = buildUser(userId, Role.ADMIN);
        when(userRepo.findById(userId)).thenReturn(Optional.of(adminUser));
        when(userRepo.findActiveByRole(Role.ADMIN)).thenReturn(List.of(adminUser));

        assertThatThrownBy(() -> userService.deleteUser(userId, adminActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("last active admin");
    }

    @Test
    void deleteUser_regularStaff_setsDeleted() {
        UUID userId = UUID.randomUUID();
        User staff = buildUser(userId, Role.STAFF);
        when(userRepo.findById(userId)).thenReturn(Optional.of(staff));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteUser(userId, adminActor);

        assertThat(staff.getStatus()).isEqualTo(AccountStatus.DELETED);
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Test
    void updateUser_demoteLastAdmin_throwsConflict() {
        UUID userId = UUID.randomUUID();
        User admin = buildUser(userId, Role.ADMIN);
        when(userRepo.findById(userId)).thenReturn(Optional.of(admin));
        when(userRepo.findActiveByRole(Role.ADMIN)).thenReturn(List.of(admin));

        assertThatThrownBy(() -> userService.updateUser(
                userId, null, null, "STAFF", adminActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("last active admin");
    }

    @Test
    void updateUser_toCustomerRole_throwsValidation() {
        UUID userId = UUID.randomUUID();
        User staff = buildUser(userId, Role.STAFF);
        when(userRepo.findById(userId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> userService.updateUser(
                userId, null, null, "CUSTOMER", adminActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("CUSTOMER");
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_wrongCurrentPassword_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, Role.STAFF);
        user.setPasswordHash(passwordEncoder.encode("correct-pass"));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        var req = new com.acetelecom.repairdesk.feature.user.dto.ChangePasswordRequest(
                "wrong-pass", "NewPass@1234");

        assertThatThrownBy(() -> userService.changePassword(userId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("incorrect");
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private User buildUser(UUID id, Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setStatus(AccountStatus.ACTIVE);
        u.setUsername("user_" + id.toString().substring(0, 8));
        u.setName("Test User");
        u.setMobile("9000000000");
        u.setPasswordHash(passwordEncoder.encode("Pass@1234"));
        return u;
    }
}
