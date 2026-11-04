package com.acetelecom.repairdesk.feature.location.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.location.dto.AttendanceFixRequest;
import com.acetelecom.repairdesk.feature.location.dto.AttendanceRowResponse;
import com.acetelecom.repairdesk.feature.location.dto.MyAttendanceResponse;
import com.acetelecom.repairdesk.feature.location.service.AttendanceClassifier.Fix;
import com.acetelecom.repairdesk.feature.location.service.AttendanceService;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService service;
    private final CurrentUser currentUser;

    public AttendanceController(AttendanceService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/start")
    @Secured("ROLE_TECHNICIAN")
    public MyAttendanceResponse start(@Valid @RequestBody(required = false) AttendanceFixRequest body) {
        return service.start(currentUser.get().id(), toFix(body));
    }

    @PostMapping("/end")
    @Secured("ROLE_TECHNICIAN")
    public MyAttendanceResponse end(@Valid @RequestBody(required = false) AttendanceFixRequest body) {
        return service.end(currentUser.get().id(), toFix(body));
    }

    @GetMapping("/today")
    @Secured("ROLE_TECHNICIAN")
    public MyAttendanceResponse today() {
        return service.today(currentUser.get().id());
    }

    @GetMapping
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public List<AttendanceRowResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID technicianId) {
        return service.list(from, to, technicianId);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID technicianId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(service.csv(from, to, technicianId));
    }

    private static Fix toFix(AttendanceFixRequest body) {
        if (body == null) {
            return null;
        }
        return new Fix(body.latitude(), body.longitude(), body.accuracyM(), Boolean.TRUE.equals(body.mocked()));
    }
}
