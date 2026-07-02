package com.acetelecom.repairdesk.feature.request.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.common.web.PageResponse;
import com.acetelecom.repairdesk.feature.job.dto.JobResponse;
import com.acetelecom.repairdesk.feature.job.service.JobService;
import com.acetelecom.repairdesk.feature.request.dto.CreateRequestDto;
import com.acetelecom.repairdesk.feature.request.dto.RepairRequestResponse;
import com.acetelecom.repairdesk.feature.request.dto.RepairRequestSummary;
import com.acetelecom.repairdesk.feature.request.dto.UpdateRequestDto;
import com.acetelecom.repairdesk.feature.request.service.RepairRequestService;
import com.acetelecom.repairdesk.security.AuthenticatedUser;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/requests")
public class RepairRequestController {

    private final RepairRequestService service;
    private final JobService jobService;
    private final CurrentUser currentUser;

    public RepairRequestController(RepairRequestService service, JobService jobService,
            CurrentUser currentUser) {
        this.service = service;
        this.jobService = jobService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    public ResponseEntity<RepairRequestResponse> create(@Valid @RequestBody CreateRequestDto dto) {
        AuthenticatedUser caller = currentUser.get();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, caller));
    }

    @GetMapping("/{id}")
    public RepairRequestResponse get(@PathVariable UUID id) {
        return service.get(id, currentUser.get());
    }

    @PutMapping("/{id}")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    public RepairRequestResponse update(@PathVariable UUID id,
            @Valid @RequestBody UpdateRequestDto dto) {
        return service.update(id, dto, currentUser.get());
    }

    @PostMapping("/{id}/cancel")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        service.cancel(id, currentUser.get());
        return ResponseEntity.noContent().build();
    }

    /** Customer's own request list. */
    @GetMapping("/my")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    public PageResponse<RepairRequestSummary> myRequests(Pageable pageable) {
        return service.getMyRequests(currentUser.get(), pageable);
    }

    /** Staff / Admin queue. */
    @GetMapping
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public PageResponse<RepairRequestSummary> queue(
            @RequestParam(required = false) String status, Pageable pageable) {
        return service.getQueue(status, pageable);
    }

    // ── Review actions (RV-002, RV-003, RV-004) ───────────────────────────

    @PostMapping("/{id}/approve")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public ResponseEntity<JobResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.approve(id, currentUser.get()));
    }

    @PostMapping("/{id}/reject")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public ResponseEntity<Void> reject(@PathVariable UUID id,
            @RequestBody RejectRequest body) {
        jobService.reject(id, body.reasonCode(), body.customerMessage(), currentUser.get());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/request-info")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public ResponseEntity<Void> requestInfo(@PathVariable UUID id,
            @RequestBody InfoRequest body) {
        jobService.requestInfo(id, body.message(), currentUser.get());
        return ResponseEntity.noContent().build();
    }

    record RejectRequest(String reasonCode, String customerMessage) {}
    record InfoRequest(String message) {}
}
