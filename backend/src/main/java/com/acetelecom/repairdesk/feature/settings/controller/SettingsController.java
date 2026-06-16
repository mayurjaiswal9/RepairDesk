package com.acetelecom.repairdesk.feature.settings.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.settings.dto.SchedulingConfigResponse;
import com.acetelecom.repairdesk.feature.settings.service.SettingsService;

@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    /** Available to all authenticated users — used by the request intake form and scheduling UI. */
    @GetMapping("/scheduling-config")
    public SchedulingConfigResponse schedulingConfig() {
        return service.getSchedulingConfig();
    }
}
