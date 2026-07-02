package com.acetelecom.repairdesk.feature.user.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.dto.CreateUserRequest;
import com.acetelecom.repairdesk.feature.user.dto.UserProfile;
import com.acetelecom.repairdesk.feature.user.service.UserService;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserManagementController {

    private final UserService service;
    private final CurrentUser currentUser;

    public UserManagementController(UserService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** List users — all (admin), or filter by role (e.g. role=TECHNICIAN for assignment dropdowns). */
    @GetMapping
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public List<UserProfile> list(@RequestParam(required = false) String role) {
        if (role != null) {
            try {
                return service.listByRole(Role.valueOf(role.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        return service.listAll();
    }

    @PostMapping
    @Secured("ROLE_ADMIN")
    public ResponseEntity<UserProfile> create(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(req, currentUser.get()));
    }

    @PostMapping("/{id}/activate")
    @Secured("ROLE_ADMIN")
    public UserProfile activate(@PathVariable UUID id) {
        return service.setStatus(id, AccountStatus.ACTIVE, currentUser.get());
    }

    @PostMapping("/{id}/deactivate")
    @Secured("ROLE_ADMIN")
    public UserProfile deactivate(@PathVariable UUID id) {
        return service.setStatus(id, AccountStatus.DISABLED, currentUser.get());
    }

    /** Edit user name / mobile / role (STAFF-003). */
    @PatchMapping("/{id}")
    @Secured("ROLE_ADMIN")
    public UserProfile update(@PathVariable UUID id, @RequestBody UpdateUserRequest req) {
        return service.updateUser(id, req.name(), req.mobile(), req.role(), currentUser.get());
    }

    /** Admin reset password (STAFF-005). */
    @PostMapping("/{id}/reset-password")
    @Secured("ROLE_ADMIN")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @RequestBody ResetPasswordRequest req) {
        service.adminResetPassword(id, req.newPassword(), currentUser.get());
    }

    /** Soft-delete a user (STAFF-004). */
    @DeleteMapping("/{id}")
    @Secured("ROLE_ADMIN")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteUser(id, currentUser.get());
    }

    record UpdateUserRequest(String name, String mobile, String role) {
    }

    record ResetPasswordRequest(String newPassword) {
    }
}
