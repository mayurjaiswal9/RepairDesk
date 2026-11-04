package com.acetelecom.repairdesk.feature.trip.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.trip.dto.MileageDayResponse;
import com.acetelecom.repairdesk.feature.trip.dto.MileageDaysResponse;
import com.acetelecom.repairdesk.feature.trip.service.MileageService;
import com.acetelecom.repairdesk.security.CurrentUser;

@RestController
@RequestMapping("/mileage")
public class MileageController {

    private final MileageService service;
    private final CurrentUser currentUser;

    public MileageController(MileageService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/days")
    @Secured({ "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public MileageDaysResponse days(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID technicianId,
            @RequestParam(required = false) String vehicle,
            @RequestParam(required = false) String show,
            @RequestParam(required = false) String view) {
        return service.days(from, to, technicianId, vehicle, show, view, currentUser.get());
    }

    @GetMapping("/days/{technicianId}/{date}")
    @Secured({ "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public MileageDayResponse day(@PathVariable UUID technicianId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.day(technicianId, date, to, currentUser.get());
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @Secured({ "ROLE_ADMIN" })
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID technicianId,
            @RequestParam(required = false) String vehicle,
            @RequestParam(required = false) String show,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String level) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mileage.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(service.csv(from, to, technicianId, vehicle, show, view, level));
    }
}
