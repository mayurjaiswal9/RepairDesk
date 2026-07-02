package com.acetelecom.repairdesk.feature.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.user.dto.ChangePasswordRequest;
import com.acetelecom.repairdesk.feature.user.dto.UpdateMobileRequest;
import com.acetelecom.repairdesk.feature.user.dto.UserProfile;
import com.acetelecom.repairdesk.feature.user.service.UserService;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/me")
public class ProfileController {

    private final UserService service;
    private final CurrentUser currentUser;

    public ProfileController(UserService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public UserProfile getMe() {
        return service.getMe(currentUser.get().id());
    }

    @PatchMapping
    public UserProfile updateMobile(@Valid @RequestBody UpdateMobileRequest req) {
        return service.updateMobile(currentUser.get().id(), req);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        service.changePassword(currentUser.get().id(), req);
        return ResponseEntity.noContent().build();
    }
}
