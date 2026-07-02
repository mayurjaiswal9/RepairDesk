package com.acetelecom.repairdesk.feature.settings.controller;

import java.time.LocalTime;
import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.dto.SchedulingConfigResponse;
import com.acetelecom.repairdesk.feature.settings.service.SettingsService;
import com.acetelecom.repairdesk.feature.settings.service.SettingsService.WorkingHoursUpdate;

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

    /** Admin: get full settings. */
    @GetMapping
    @Secured("ROLE_ADMIN")
    public BusinessSettings getSettings() {
        return service.getSettings();
    }

    /** Admin: update scheduling / business settings. */
    @PutMapping
    @Secured("ROLE_ADMIN")
    public BusinessSettings updateSettings(@RequestBody UpdateSettingsRequest body) {
        return service.updateSettings(
                body.businessName(), body.businessAddress(),
                body.businessPhone(), body.businessEmail(),
                body.slotDurationMinutes(), body.bookingHorizonDays());
    }

    /** Admin: update working hours. */
    @PutMapping("/working-hours")
    @Secured("ROLE_ADMIN")
    public SchedulingConfigResponse updateWorkingHours(
            @RequestBody List<WorkingHoursEntry> entries) {
        var updates = entries.stream()
                .map(e -> new WorkingHoursUpdate(e.dayOfWeek(), e.isClosed(),
                        e.openTime(), e.closeTime()))
                .toList();
        service.updateWorkingHours(updates);
        return service.getSchedulingConfig();
    }

    record UpdateSettingsRequest(
            String businessName,
            String businessAddress,
            String businessPhone,
            String businessEmail,
            Integer slotDurationMinutes,
            Integer bookingHorizonDays) {
    }

    record WorkingHoursEntry(int dayOfWeek, boolean isClosed,
            LocalTime openTime, LocalTime closeTime) {
    }
}
