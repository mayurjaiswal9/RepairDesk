package com.acetelecom.repairdesk.feature.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.notification.config.PushProperties;
import com.acetelecom.repairdesk.feature.notification.dto.DeviceRegistrationRequest;
import com.acetelecom.repairdesk.feature.notification.dto.VapidKeyResponse;
import com.acetelecom.repairdesk.feature.notification.dto.WebPushRegistrationRequest;
import com.acetelecom.repairdesk.feature.notification.service.PushDeviceService;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/me")
public class PushDeviceController {

    private final PushDeviceService devices;
    private final PushProperties pushProperties;
    private final CurrentUser currentUser;

    public PushDeviceController(PushDeviceService devices, PushProperties pushProperties, CurrentUser currentUser) {
        this.devices = devices;
        this.pushProperties = pushProperties;
        this.currentUser = currentUser;
    }

    @PostMapping("/devices")
    public ResponseEntity<Void> registerPhone(@Valid @RequestBody DeviceRegistrationRequest body) {
        devices.registerPhone(currentUser.get().id(), body.token(), body.platform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices")
    public ResponseEntity<Void> removePhone(@RequestBody DeviceRegistrationRequest body) {
        devices.removePhone(currentUser.get().id(), body == null ? null : body.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/push-subscriptions/key")
    public VapidKeyResponse vapidKey() {
        return new VapidKeyResponse(
                pushProperties.desktopConfigured() ? pushProperties.getVapidPublicKey() : "",
                pushProperties.desktopConfigured());
    }

    @PostMapping("/push-subscriptions")
    public ResponseEntity<Void> registerBrowser(@Valid @RequestBody WebPushRegistrationRequest body) {
        devices.registerBrowser(currentUser.get().id(), body.endpoint(), body.p256dh(), body.auth());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/push-subscriptions")
    public ResponseEntity<Void> removeBrowser(@RequestBody WebPushRegistrationRequest body) {
        devices.removeBrowser(currentUser.get().id(), body == null ? null : body.endpoint());
        return ResponseEntity.noContent().build();
    }
}
