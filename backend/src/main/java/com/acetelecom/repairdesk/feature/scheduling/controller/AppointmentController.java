package com.acetelecom.repairdesk.feature.scheduling.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.scheduling.dto.AppointmentResponse;
import com.acetelecom.repairdesk.feature.scheduling.service.AppointmentService;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final CurrentUser currentUser;

    public AppointmentController(AppointmentService appointmentService, CurrentUser currentUser) {
        this.appointmentService = appointmentService;
        this.currentUser = currentUser;
    }

    /** Create an appointment for a job (staff/admin). */
    @PostMapping("/jobs/{jobId}/appointments")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@PathVariable UUID jobId,
            @Valid @RequestBody CreateRequest body) {
        return appointmentService.create(jobId, body.technicianId(),
                body.scheduledDate(), body.startTime(), body.notes(), currentUser.get());
    }

    /** Reschedule an existing active appointment. */
    @PutMapping("/jobs/{jobId}/appointments/{appointmentId}/reschedule")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public AppointmentResponse reschedule(@PathVariable UUID jobId,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody RescheduleRequest body) {
        return appointmentService.reschedule(jobId, appointmentId,
                body.scheduledDate(), body.startTime(), body.notes(), currentUser.get());
    }

    /** Cancel an appointment. */
    @DeleteMapping("/jobs/{jobId}/appointments/{appointmentId}")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID jobId,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody CancelRequest body) {
        appointmentService.cancel(jobId, appointmentId, body.reason(), currentUser.get());
    }

    /** List appointments for a job. */
    @GetMapping("/jobs/{jobId}/appointments")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public List<AppointmentResponse> forJob(@PathVariable UUID jobId) {
        return appointmentService.forJob(jobId);
    }

    /** Calendar: all appointments in a date range (staff/admin). */
    @GetMapping("/appointments")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public List<AppointmentResponse> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointmentService.listForRange(from, to);
    }

    /** Technician's own schedule. */
    @GetMapping("/me/schedule")
    @Secured("ROLE_TECHNICIAN")
    public List<AppointmentResponse> mySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointmentService.mySchedule(currentUser.get(), from, to);
    }

    // ── Inner DTOs ──────────────────────────────────────────────────────────

    record CreateRequest(
            @NotNull UUID technicianId,
            @NotNull LocalDate scheduledDate,
            @NotNull LocalTime startTime,
            String notes) {
    }

    record RescheduleRequest(
            @NotNull LocalDate scheduledDate,
            @NotNull LocalTime startTime,
            String notes) {
    }

    record CancelRequest(@NotNull String reason) {
    }
}
