package com.acetelecom.repairdesk.feature.attachment.controller;

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

import com.acetelecom.repairdesk.feature.attachment.domain.Attachment;
import com.acetelecom.repairdesk.feature.attachment.dto.AttachmentResponse;
import com.acetelecom.repairdesk.feature.attachment.service.AttachmentService;
import com.acetelecom.repairdesk.feature.attachment.service.AttachmentService.OpenedAttachment;
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

    /**
     * List attachments for a repair request. Technicians need read access here
     * too: the Job Detail screen shows the customer's original request photos
     * alongside the job, for both admin and technician roles (BUG-8).
     */
    @GetMapping("/requests/{id}/attachments")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
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

    /**
     * Preferred download path — uses the attachment UUID so file extensions in
     * storage keys never interfere with routing.
     */
    @GetMapping("/attachments/by-id/{id}")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public ResponseEntity<InputStreamResource> streamById(@PathVariable UUID id) {
        OpenedAttachment opened = attachmentService.streamById(id, currentUser.get());
        return toInlineResponse(opened);
    }

    /**
     * Stream / download an attachment by storage key (FILE-003, legacy).
     * Regex {@code .+} keeps file extensions (e.g. {@code uuid.jpg}) in the
     * path variable.
     */
    @GetMapping("/attachments/{storageKey:.+}")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public ResponseEntity<InputStreamResource> stream(
            @PathVariable String storageKey) {
        OpenedAttachment opened = attachmentService.stream(storageKey, currentUser.get());
        return toInlineResponse(opened);
    }

    /** Delete an attachment. */
    @DeleteMapping("/attachments/{id}")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        attachmentService.delete(id, currentUser.get());
    }

    private static ResponseEntity<InputStreamResource> toInlineResponse(OpenedAttachment opened) {
        Attachment att = opened.attachment();
        MediaType mediaType = resolveMediaType(att.getContentType());
        String filename = att.getOriginalFilename() != null && !att.getOriginalFilename().isBlank()
                ? att.getOriginalFilename()
                : att.getStorageKey();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filename.replace("\"", "") + "\"")
                .contentType(mediaType)
                .body(new InputStreamResource(opened.stream()));
    }

    /** Prefer stored MIME; fall back to image/jpeg for legacy octet-stream rows. */
    private static MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.IMAGE_JPEG;
        }
        try {
            MediaType parsed = MediaType.parseMediaType(contentType);
            if (MediaType.APPLICATION_OCTET_STREAM.includes(parsed)) {
                return MediaType.IMAGE_JPEG;
            }
            return parsed;
        } catch (Exception e) {
            return MediaType.IMAGE_JPEG;
        }
    }
}
