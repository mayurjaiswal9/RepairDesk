package com.acetelecom.repairdesk.feature.location.controller;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.location.dto.LocationSettingsResponse;
import com.acetelecom.repairdesk.feature.location.dto.UpdateLocationSettingsRequest;
import com.acetelecom.repairdesk.feature.location.service.LocationSettingsService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/settings/location")
public class LocationSettingsController {

    private final LocationSettingsService service;

    public LocationSettingsController(LocationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @Secured("ROLE_ADMIN")
    public LocationSettingsResponse get() {
        return service.get();
    }

    @PutMapping
    @Secured("ROLE_ADMIN")
    public LocationSettingsResponse update(@Valid @RequestBody UpdateLocationSettingsRequest body) {
        return service.update(body);
    }
}
