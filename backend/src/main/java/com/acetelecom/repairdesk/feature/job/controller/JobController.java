package com.acetelecom.repairdesk.feature.job.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.common.web.PageResponse;
import com.acetelecom.repairdesk.feature.job.dto.JobResponse;
import com.acetelecom.repairdesk.feature.job.dto.JobSummary;
import com.acetelecom.repairdesk.feature.job.service.JobService;
import com.acetelecom.repairdesk.security.AuthenticatedUser;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService service;
    private final CurrentUser currentUser;

    public JobController(JobService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public PageResponse<JobSummary> list(@RequestParam(required = false) String status,
            Pageable pageable) {
        AuthenticatedUser caller = currentUser.get();
        if ("TECHNICIAN".equals(caller.role())) {
            return service.listForTechnician(caller.id(), pageable);
        }
        return service.listAll(status, pageable);
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.get());
    }

    @PostMapping("/{id}/assign")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public JobResponse assign(@PathVariable UUID id,
            @RequestBody AssignRequest body) {
        return service.assign(id, body.technicianId(), currentUser.get());
    }

    @PostMapping("/{id}/unassign")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public JobResponse unassign(@PathVariable UUID id) {
        return service.unassign(id, currentUser.get());
    }

    @PostMapping("/{id}/start")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public JobResponse start(@PathVariable UUID id) {
        return service.start(id, currentUser.get());
    }

    @PostMapping("/{id}/awaiting-parts")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public JobResponse awaitingParts(@PathVariable UUID id) {
        return service.awaitingParts(id, currentUser.get());
    }

    @PostMapping("/{id}/resume")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public JobResponse resume(@PathVariable UUID id) {
        return service.resume(id, currentUser.get());
    }

    @PostMapping("/{id}/complete")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public JobResponse complete(@PathVariable UUID id,
            @RequestBody CompleteRequest body) {
        return service.complete(id, body.resolutionSummary(), currentUser.get());
    }

    @PostMapping("/{id}/cancel")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public ResponseEntity<Void> cancel(@PathVariable UUID id,
            @RequestBody ReasonRequest body) {
        service.cancel(id, body.reason(), currentUser.get());
        return ResponseEntity.noContent().build();
    }

    // ── JOB-006: Priority, warranty, notes ───────────────────────────────────

    @PostMapping("/{id}/priority")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public JobResponse setPriority(@PathVariable UUID id,
            @RequestBody PriorityRequest body) {
        return service.setPriority(id, body.priority(), currentUser.get());
    }

    @PostMapping("/{id}/warranty")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public JobResponse setWarranty(@PathVariable UUID id,
            @RequestBody WarrantyRequest body) {
        return service.setWarranty(id, body.confirmed(), currentUser.get());
    }

    @PostMapping("/{id}/notes")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public JobResponse appendNote(@PathVariable UUID id,
            @RequestBody NoteRequest body) {
        return service.appendNote(id, body.note(), currentUser.get());
    }

    record AssignRequest(UUID technicianId) {}
    record CompleteRequest(@NotBlank String resolutionSummary) {}
    record ReasonRequest(@NotBlank String reason) {}
    record PriorityRequest(String priority) {}
    record WarrantyRequest(boolean confirmed) {}
    record NoteRequest(String note) {}
}
