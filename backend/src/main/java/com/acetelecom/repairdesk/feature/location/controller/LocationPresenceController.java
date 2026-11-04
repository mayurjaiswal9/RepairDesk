package com.acetelecom.repairdesk.feature.location.controller;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.location.dto.ReportLocationRequest;
import com.acetelecom.repairdesk.feature.location.service.TechnicianPresenceService;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/me/location")
public class LocationPresenceController {

    private final TechnicianPresenceService service;
    private final CurrentUser currentUser;

    public LocationPresenceController(TechnicianPresenceService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Secured("ROLE_TECHNICIAN")
    public void report(@Valid @RequestBody ReportLocationRequest body) {
        service.report(currentUser.get(), body);
    }
}
