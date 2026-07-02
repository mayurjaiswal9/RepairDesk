package com.acetelecom.repairdesk.feature.job.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.common.web.PageResponse;
import com.acetelecom.repairdesk.feature.audit.service.AuditService;
import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.job.dto.JobResponse;
import com.acetelecom.repairdesk.feature.job.dto.JobSummary;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.notification.service.NotificationService;
import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

@Service
@Transactional
public class JobService {

    private final JobRepository jobRepo;
    private final RepairRequestRepository requestRepo;
    private final UserRepository userRepo;
    private final NotificationService notifService;
    private final AuditService auditService;

    public JobService(JobRepository jobRepo, RepairRequestRepository requestRepo,
            UserRepository userRepo, NotificationService notifService,
            AuditService auditService) {
        this.jobRepo = jobRepo;
        this.requestRepo = requestRepo;
        this.userRepo = userRepo;
        this.notifService = notifService;
        this.auditService = auditService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Review actions (RV-002, RV-003, RV-004)
    // ─────────────────────────────────────────────────────────────────────────

    /** Approve a PENDING_REVIEW request → atomically create a Job (CREATED). */
    public JobResponse approve(UUID requestId, AuthenticatedUser actor) {
        RepairRequest req = requireRequest(requestId);
        if (req.getStatus() != RequestStatus.PENDING_REVIEW
                && req.getStatus() != RequestStatus.INFORMATION_REQUIRED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Request cannot be approved in status " + req.getStatus());
        }
        if (jobRepo.findByRequestId(requestId).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "Job already exists for this request");
        }

        req.setStatus(RequestStatus.APPROVED);
        req.setReviewedAt(Instant.now());
        requestRepo.save(req);

        Job job = new Job();
        job.setRequest(req);
        job.setReferenceNumber(req.getReferenceNumber());
        job.setCustomer(req.getCustomer());
        job.setDeviceCategory(req.getDeviceCategory());
        job.setContactName(req.getContactName());
        job.setContactMobile(req.getContactMobile());
        job.setDeviceBrand(req.getDeviceBrand());
        job.setDeviceModel(req.getDeviceModel());
        job.setDeviceSerial(req.getDeviceSerial());
        job.setIssueDescription(req.getIssueDescription());
        job.setPurchaseDate(req.getPurchaseDate());
        job.setPriority(req.getPriority());
        Job saved = jobRepo.save(job);

        notifService.send(req.getCustomer().getId(), "JOB_APPROVED",
                "Your repair request " + req.getReferenceNumber()
                        + " has been accepted and is being prepared.",
                req.getReferenceNumber(), saved.getId());

        auditService.record(actor, "REQUEST", requestId, "APPROVED", null);
        auditService.record(actor, "JOB", saved.getId(), "CREATED",
                "Auto-created from request " + req.getReferenceNumber());

        return JobResponse.from(saved);
    }

    /** Reject a request with a mandatory reason code and customer-facing message. */
    public void reject(UUID requestId, String reasonCode, String customerMessage,
            AuthenticatedUser actor) {
        RepairRequest req = requireRequest(requestId);
        if (req.getStatus() != RequestStatus.PENDING_REVIEW
                && req.getStatus() != RequestStatus.INFORMATION_REQUIRED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Request cannot be rejected in status " + req.getStatus());
        }
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Rejection reason code is required");
        }

        req.setStatus(RequestStatus.REJECTED);
        req.setRejectionReasonCode(reasonCode);
        req.setCustomerFacingMessage(customerMessage);
        req.setReviewedAt(Instant.now());
        requestRepo.save(req);

        notifService.send(req.getCustomer().getId(), "REQUEST_REJECTED",
                customerMessage != null && !customerMessage.isBlank()
                        ? customerMessage
                        : "Your repair request " + req.getReferenceNumber()
                                + " could not be accepted at this time.",
                req.getReferenceNumber(), requestId);

        auditService.record(actor, "REQUEST", requestId, "REJECTED",
                "Reason: " + reasonCode);
    }

    /** Set request to INFORMATION_REQUIRED with a message to the customer. */
    public void requestInfo(UUID requestId, String message, AuthenticatedUser actor) {
        RepairRequest req = requireRequest(requestId);
        if (req.getStatus() != RequestStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot request info in status " + req.getStatus());
        }
        if (message == null || message.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Message is required");
        }

        req.setStatus(RequestStatus.INFORMATION_REQUIRED);
        req.setInfoRequestedMessage(message);
        requestRepo.save(req);

        notifService.send(req.getCustomer().getId(), "INFO_REQUIRED",
                "More information is needed for your request "
                        + req.getReferenceNumber() + ": " + message,
                req.getReferenceNumber(), requestId);

        auditService.record(actor, "REQUEST", requestId, "INFO_REQUESTED", message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job lifecycle (JOB-002, JOB-003, JOB-004, JOB-005)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<JobSummary> listAll(String status, Pageable pageable) {
        Page<Job> page = jobRepo.findAll(status, pageable);
        return PageResponse.from(page.map(JobSummary::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobSummary> listForTechnician(UUID techId, Pageable pageable) {
        Page<Job> page = jobRepo.findByAssignedTechnicianIdOrderByUpdatedAtDesc(techId, pageable);
        return PageResponse.from(page.map(JobSummary::from));
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID id, AuthenticatedUser caller) {
        Job job = requireJob(id);
        checkReadAccess(job, caller);
        return JobResponse.from(job);
    }

    public JobResponse assign(UUID jobId, UUID technicianId, AuthenticatedUser actor) {
        Job job = requireJob(jobId);
        assertStaffOrAdmin(actor);

        if (job.getStatus() != JobStatus.CREATED && job.getStatus() != JobStatus.ASSIGNED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot assign technician in status " + job.getStatus());
        }

        User tech = userRepo.findById(technicianId)
                .filter(u -> u.getRole() == Role.TECHNICIAN && u.isActive())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Active technician not found"));

        job.setAssignedTechnician(tech);
        job.setStatus(JobStatus.ASSIGNED);
        job.setUpdatedAt(Instant.now());
        jobRepo.save(job);

        notifService.send(technicianId, "JOB_ASSIGNED",
                "You have been assigned to job " + job.getReferenceNumber() + ".",
                job.getReferenceNumber(), job.getId());
        auditService.record(actor, "JOB", jobId, "ASSIGNED", "Technician: " + tech.getUsername());

        return JobResponse.from(job);
    }

    public JobResponse unassign(UUID jobId, AuthenticatedUser actor) {
        Job job = requireJob(jobId);
        assertStaffOrAdmin(actor);

        if (job.getStatus() != JobStatus.ASSIGNED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot unassign in status " + job.getStatus());
        }

        job.setAssignedTechnician(null);
        job.setStatus(JobStatus.CREATED);
        job.setUpdatedAt(Instant.now());
        jobRepo.save(job);
        auditService.record(actor, "JOB", jobId, "UNASSIGNED", null);
        return JobResponse.from(job);
    }

    public JobResponse start(UUID jobId, AuthenticatedUser actor) {
        Job job = requireJob(jobId);
        assertCanTransitionJob(job, actor, JobStatus.ASSIGNED, JobStatus.SCHEDULED);

        job.setStatus(JobStatus.IN_PROGRESS);
        job.setUpdatedAt(Instant.now());
        jobRepo.save(job);

        notifService.send(job.getCustomer().getId(), "JOB_IN_PROGRESS",
                "Work has started on your repair " + job.getReferenceNumber() + ".",
                job.getReferenceNumber(), job.getId());
        auditService.record(actor, "JOB", jobId, "STARTED", null);
        return JobResponse.from(job);
    }

    public JobResponse awaitingParts(UUID jobId, AuthenticatedUser actor) {
        Job job = requireJob(jobId);
        assertCanTransitionJob(job, actor, JobStatus.IN_PROGRESS);

        job.setStatus(JobStatus.AWAITING_PARTS);
        job.setUpdatedAt(Instant.now());
        jobRepo.save(job);

        notifService.send(job.getCustomer().getId(), "JOB_AWAITING_PARTS",
                "Your repair " + job.getReferenceNumber()
                        + " is on hold while we source parts.",
                job.getReferenceNumber(), job.getId());
        auditService.record(actor, "JOB", jobId, "AWAITING_PARTS", null);
        return JobResponse.from(job);
    }

    public JobResponse resume(UUID jobId, AuthenticatedUser actor) {
        Job job = requireJob(jobId);
        assertCanTransitionJob(job, actor, JobStatus.AWAITING_PARTS);

        job.setStatus(JobStatus.IN_PROGRESS);
        job.setUpdatedAt(Instant.now());
        jobRepo.save(job);
        auditService.record(actor, "JOB", jobId, "RESUMED", null);
        return JobResponse.from(job);
    }

    public JobResponse complete(UUID jobId, String resolutionSummary, AuthenticatedUser actor) {
        Job job = requireJob(jobId);
        assertCanTransitionJob(job, actor, JobStatus.IN_PROGRESS, JobStatus.AWAITING_PARTS);

        if (resolutionSummary == null || resolutionSummary.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Resolution summary is required");
        }

        job.setStatus(JobStatus.COMPLETED);
        job.setResolutionSummary(resolutionSummary);
        job.setUpdatedAt(Instant.now());
        jobRepo.save(job);

        notifService.send(job.getCustomer().getId(), "JOB_COMPLETED",
                "Your repair " + job.getReferenceNumber()
                        + " is complete and ready for collection.",
                job.getReferenceNumber(), job.getId());
        auditService.record(actor, "JOB", jobId, "COMPLETED", resolutionSummary);
        return JobResponse.from(job);
    }

    public void cancel(UUID jobId, String reason, AuthenticatedUser actor) {
        Job job = requireJob(jobId);
        assertStaffOrAdmin(actor);

        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Cancellation reason is required");
        }
        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot cancel job in status " + job.getStatus());
        }

        job.setStatus(JobStatus.CANCELLED);
        job.setCancelReason(reason);
        job.setUpdatedAt(Instant.now());
        jobRepo.save(job);

        notifService.send(job.getCustomer().getId(), "JOB_CANCELLED",
                "Your repair " + job.getReferenceNumber()
                        + " has been cancelled. Reason: " + reason,
                job.getReferenceNumber(), job.getId());
        auditService.record(actor, "JOB", jobId, "CANCELLED", reason);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private RepairRequest requireRequest(UUID id) {
        return requestRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Request not found"));
    }

    private Job requireJob(UUID id) {
        return jobRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Job not found"));
    }

    private void checkReadAccess(Job job, AuthenticatedUser caller) {
        boolean isOwner = job.getCustomer().getId().equals(caller.id());
        boolean isStaff = "STAFF".equals(caller.role()) || "ADMIN".equals(caller.role());
        boolean isTech = "TECHNICIAN".equals(caller.role())
                && job.getAssignedTechnician() != null
                && job.getAssignedTechnician().getId().equals(caller.id());
        if (!isOwner && !isStaff && !isTech) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }
    }

    private void assertStaffOrAdmin(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.role()) && !"ADMIN".equals(actor.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Staff or Admin role required");
        }
    }

    // ── JOB-006: Priority, warranty, notes ───────────────────────────────────

    public JobResponse setPriority(UUID id, String priorityName, AuthenticatedUser actor) {
        assertStaffOrAdmin(actor);
        Job job = requireJob(id);
        com.acetelecom.repairdesk.feature.request.domain.Priority p;
        try {
            p = com.acetelecom.repairdesk.feature.request.domain.Priority
                    .valueOf(priorityName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Unknown priority: " + priorityName);
        }
        String old = job.getPriority() != null ? job.getPriority().name() : "null";
        job.setPriority(p);
        jobRepo.save(job);
        auditService.record(actor, "JOB", id, "PRIORITY_CHANGED",
                old + " → " + p);
        return JobResponse.from(job);
    }

    public JobResponse setWarranty(UUID id, boolean confirmed, AuthenticatedUser actor) {
        Job job = requireJob(id);
        assertCanReadWrite(job, actor);
        job.setWarrantyConfirmed(confirmed);
        jobRepo.save(job);
        auditService.record(actor, "JOB", id, "WARRANTY_UPDATED",
                "warrantyConfirmed=" + confirmed);
        return JobResponse.from(job);
    }

    public JobResponse appendNote(UUID id, String note, AuthenticatedUser actor) {
        Job job = requireJob(id);
        assertCanReadWrite(job, actor);
        String existing = job.getInternalNotes();
        String combined = (existing != null && !existing.isBlank())
                ? existing + "\n\n--- " + Instant.now() + " ---\n" + note
                : note;
        job.setInternalNotes(combined);
        jobRepo.save(job);
        auditService.record(actor, "JOB", id, "NOTE_ADDED",
                "note appended by " + actor.username());
        return JobResponse.from(job);
    }

    private void assertCanReadWrite(Job job, AuthenticatedUser actor) {
        boolean isTech = "TECHNICIAN".equals(actor.role());
        boolean isStaff = "STAFF".equals(actor.role()) || "ADMIN".equals(actor.role());
        if (isTech && !job.canTechnicianTransition(actor.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Technician can only modify their assigned jobs");
        }
        if (!isTech && !isStaff) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Insufficient role");
        }
    }

    @SafeVarargs
    private <T extends Enum<T>> void assertCanTransitionJob(Job job, AuthenticatedUser actor,
            T... allowedFromStatuses) {
        boolean isTech = "TECHNICIAN".equals(actor.role());
        boolean isStaff = "STAFF".equals(actor.role()) || "ADMIN".equals(actor.role());

        if (isTech && !job.canTechnicianTransition(actor.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Technician can only transition their assigned jobs");
        }
        if (!isTech && !isStaff) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Insufficient role");
        }

        boolean inAllowedStatus = false;
        for (T s : allowedFromStatuses) {
            if (job.getStatus().name().equals(s.name())) {
                inAllowedStatus = true;
                break;
            }
        }
        if (!inAllowedStatus) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Transition not allowed from status " + job.getStatus());
        }
    }
}
