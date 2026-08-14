package com.acetelecom.repairdesk.feature.request.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.common.web.PageResponse;
import com.acetelecom.repairdesk.feature.audit.service.AuditService;
import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;
import com.acetelecom.repairdesk.feature.devicecategory.repository.DeviceCategoryRepository;
import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.request.domain.Priority;
import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;
import com.acetelecom.repairdesk.feature.request.dto.CreateRequestDto;
import com.acetelecom.repairdesk.feature.request.dto.RepairRequestResponse;
import com.acetelecom.repairdesk.feature.request.dto.RepairRequestSummary;
import com.acetelecom.repairdesk.feature.request.dto.UpdateRequestDto;
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

@Service
@Transactional
public class RepairRequestService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final RepairRequestRepository requestRepo;
    private final DeviceCategoryRepository categoryRepo;
    private final UserRepository userRepo;
    private final JobRepository jobRepo;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public RepairRequestService(RepairRequestRepository requestRepo,
            DeviceCategoryRepository categoryRepo,
            UserRepository userRepo,
            JobRepository jobRepo,
            JdbcTemplate jdbc,
            AuditService auditService) {
        this.requestRepo = requestRepo;
        this.categoryRepo = categoryRepo;
        this.userRepo = userRepo;
        this.jobRepo = jobRepo;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    public RepairRequestResponse create(CreateRequestDto dto, AuthenticatedUser caller) {
        User customer = userRepo.findById(caller.id())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        DeviceCategory category = categoryRepo.findById(dto.deviceCategoryId())
                .filter(c -> !c.isArchived())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Device category not found or archived"));

        String refNumber = generateReferenceNumber();

        RepairRequest req = new RepairRequest();
        req.setReferenceNumber(refNumber);
        req.setCustomer(customer);
        req.setDeviceCategory(category);
        req.setContactName(dto.contactName());
        req.setContactMobile(dto.contactMobile());
        req.setContactEmail(dto.contactEmail());
        req.setDeviceBrand(dto.deviceBrand());
        req.setDeviceModel(dto.deviceModel());
        req.setDeviceSerial(dto.deviceSerial());
        req.setPurchaseDate(dto.purchaseDate());
        req.setWarrantyStatus(dto.warrantyStatus());
        req.setIssueDescription(dto.issueDescription());
        req.setPreferredDate(dto.preferredDate());
        req.setPreferredTime(dto.preferredTime());

        return RepairRequestResponse.from(requestRepo.save(req), null);
    }

    @Transactional(readOnly = true)
    public RepairRequestResponse get(UUID id, AuthenticatedUser caller) {
        RepairRequest req = requireById(id);
        checkReadAccess(req, caller);
        return RepairRequestResponse.from(req, findJobOrNull(req.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<RepairRequestSummary> getMyRequests(AuthenticatedUser caller, Pageable pageable) {
        return getMyRequests(caller.id(), pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<RepairRequestSummary> getMyRequests(UUID customerId, Pageable pageable) {
        Page<RepairRequest> page = requestRepo
                .findAllByCustomerIdOrderByUpdatedAtDescCreatedAtDesc(customerId, pageable);
        return PageResponse.from(page.map(r -> RepairRequestSummary.from(r, findJobOrNull(r.getId()))));
    }

    @Transactional(readOnly = true)
    public PageResponse<RepairRequestSummary> getQueue(String status, UUID categoryId, String q,
            Pageable pageable) {
        RequestStatus statusEnum = (status == null || status.isBlank())
                ? null : RequestStatus.valueOf(status.toUpperCase());
        String query = (q == null || q.isBlank()) ? null : q.trim();
        Page<RepairRequest> page = requestRepo.findQueueFiltered(statusEnum, categoryId, query, pageable);
        return PageResponse.from(page.map(r -> RepairRequestSummary.from(r, findJobOrNull(r.getId()))));
    }

    /** The Job is created 1:1 on approval; null for requests not yet approved. */
    private Job findJobOrNull(UUID requestId) {
        return jobRepo.findByRequestId(requestId).orElse(null);
    }

    public RepairRequestResponse update(UUID id, UpdateRequestDto dto, AuthenticatedUser caller) {
        RepairRequest req = requireById(id);
        assertOwner(req, caller);

        if (!req.isEditableByOwner()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Request cannot be edited in status " + req.getStatus());
        }

        req.setContactName(dto.contactName());
        req.setContactMobile(dto.contactMobile());
        req.setContactEmail(dto.contactEmail());
        req.setDeviceBrand(dto.deviceBrand());
        req.setDeviceModel(dto.deviceModel());
        req.setDeviceSerial(dto.deviceSerial());
        req.setWarrantyStatus(dto.warrantyStatus());
        req.setIssueDescription(dto.issueDescription());
        req.setPreferredDate(dto.preferredDate());
        req.setPreferredTime(dto.preferredTime());
        req.setUpdatedAt(Instant.now());

        return RepairRequestResponse.from(requestRepo.save(req), null);
    }

    public void cancel(UUID id, AuthenticatedUser caller) {
        RepairRequest req = requireById(id);
        assertOwner(req, caller);

        if (!req.isCancellableByOwner()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Request cannot be cancelled in status " + req.getStatus());
        }

        req.setStatus(RequestStatus.CANCELLED);
        req.setUpdatedAt(Instant.now());
        requestRepo.save(req);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String generateReferenceNumber() {
        int year = ZonedDateTime.now(IST).getYear();
        long seq = jdbc.queryForObject("SELECT nextval('repair_reference_seq')", Long.class);
        return String.format("RD-%d%06d", year, seq);
    }

    private RepairRequest requireById(UUID id) {
        return requestRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Repair request not found"));
    }

    private void checkReadAccess(RepairRequest req, AuthenticatedUser caller) {
        boolean isOwner = req.isOwnedBy(caller.id());
        boolean isStaff = "STAFF".equals(caller.role()) || "ADMIN".equals(caller.role());
        if (!isOwner && !isStaff) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }
    }

    // ── RV-005: Priority ─────────────────────────────────────────────────────

    public RepairRequestResponse setPriority(UUID id, String priorityName,
            AuthenticatedUser actor) {
        assertStaffOrAdmin(actor);
        RepairRequest req = findById(id);
        Priority priority;
        try {
            priority = Priority.valueOf(priorityName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Unknown priority: " + priorityName);
        }
        String old = req.getPriority().name();
        req.setPriority(priority);
        requestRepo.save(req);
        auditService.record(actor, "REQUEST", id, "PRIORITY_CHANGED",
                old + " → " + priority);
        return RepairRequestResponse.from(req, findJobOrNull(req.getId()));
    }

    // ── RV-005: Internal notes ────────────────────────────────────────────────

    public RepairRequestResponse appendNote(UUID id, String note,
            AuthenticatedUser actor) {
        assertStaffOrAdmin(actor);
        RepairRequest req = findById(id);
        auditService.record(actor, "REQUEST", id, "NOTE_ADDED", note);
        return RepairRequestResponse.from(req, findJobOrNull(req.getId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertOwner(RepairRequest req, AuthenticatedUser caller) {
        if (!req.isOwnedBy(caller.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }
    }

    private void assertStaffOrAdmin(AuthenticatedUser actor) {
        String role = actor.role();
        if (!"STAFF".equals(role) && !"ADMIN".equals(role)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Staff or Admin access required");
        }
    }

    private RepairRequest findById(UUID id) {
        return requestRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Repair request not found"));
    }
}
