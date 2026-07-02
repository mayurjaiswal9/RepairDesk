package com.acetelecom.repairdesk.feature.attachment.controller;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.acetelecom.repairdesk.feature.attachment.dto.AttachmentResponse;
import com.acetelecom.repairdesk.feature.attachment.service.AttachmentService;
import com.acetelecom.repairdesk.security.CurrentUser;

@RestController
@RequestMapping("")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final CurrentUser currentUser;

    public AttachmentController(AttachmentService attachmentService,
            CurrentUser currentUser) {
        this.attachmentService = attachmentService;
        this.currentUser = currentUser;
    }

    /** Upload attachment to a repair request. */
    @PostMapping("/requests/{id}/attachments")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse uploadToRequest(@PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return attachmentService.upload("REPAIR_REQUEST", id, file, currentUser.get());
    }

    /** List attachments for a repair request. */
    @GetMapping("/requests/{id}/attachments")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    public List<AttachmentResponse> listForRequest(@PathVariable UUID id) {
        return attachmentService.list("REPAIR_REQUEST", id);
    }

    /** Upload attachment to a job (internal). */
    @PostMapping("/jobs/{id}/attachments")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse uploadToJob(@PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return attachmentService.upload("JOB", id, file, currentUser.get());
    }

    /** List attachments for a job (internal). */
    @GetMapping("/jobs/{id}/attachments")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public List<AttachmentResponse> listForJob(@PathVariable UUID id) {
        return attachmentService.list("JOB", id);
    }

    /** Stream / download an attachment by storage key (FILE-003). */
    @GetMapping("/attachments/{storageKey}")
    public ResponseEntity<InputStreamResource> stream(
            @PathVariable String storageKey) {
        InputStream is = attachmentService.stream(storageKey, currentUser.get());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + storageKey + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(is));
    }

    /** Delete an attachment. */
    @DeleteMapping("/attachments/{id}")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        attachmentService.delete(id, currentUser.get());
    }
}
