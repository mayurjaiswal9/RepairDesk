package com.acetelecom.repairdesk.feature.location.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.location.dto.LocationConfigResponse;
import com.acetelecom.repairdesk.feature.location.service.LocationSettingsService;

@RestController
@RequestMapping("/location/config")
public class LocationConfigController {

    private final LocationSettingsService service;

    public LocationConfigController(LocationSettingsService service) {
        this.service = service;
    }

    /** Any signed-in user. Feature switches and the shop pin; no allowance rates. */
    @GetMapping
    public LocationConfigResponse config() {
        return service.config();
    }
}
