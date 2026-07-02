package com.acetelecom.repairdesk.feature.tracking.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.audit.repository.AuditEventRepository;
import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;
import com.acetelecom.repairdesk.feature.tracking.dto.RepairDetail;
import com.acetelecom.repairdesk.feature.tracking.dto.RepairListItem;

@Service
@Transactional(readOnly = true)
public class TrackingService {

    // Customer-friendly status labels (spec: repair-status-tracking.md)
    private static final Map<String, String> REQUEST_LABELS = Map.of(
            "PENDING_REVIEW", "Submitted — Under Review",
            "INFORMATION_REQUIRED", "More Information Needed",
            "APPROVED", "Accepted — Being Prepared",
            "REJECTED", "Not Accepted",
            "CANCELLED", "Cancelled");

    private static final Map<String, String> JOB_LABELS = Map.of(
            "CREATED", "Accepted — Being Prepared",
            "ASSIGNED", "Assigned to Technician",
            "SCHEDULED", "Appointment Scheduled",
            "IN_PROGRESS", "Repair In Progress",
            "AWAITING_PARTS", "Waiting for Parts",
            "COMPLETED", "Repair Completed",
            "CANCELLED", "Cancelled");

    private final RepairRequestRepository requestRepo;
    private final JobRepository jobRepo;
    private final AuditEventRepository auditRepo;

    public TrackingService(RepairRequestRepository requestRepo, JobRepository jobRepo,
            AuditEventRepository auditRepo) {
        this.requestRepo = requestRepo;
        this.jobRepo = jobRepo;
        this.auditRepo = auditRepo;
    }

    public List<RepairListItem> list(UUID customerId) {
        List<RepairRequest> requests = requestRepo
                .findAllByCustomerIdOrderByUpdatedAtDescCreatedAtDesc(customerId,
                        Pageable.unpaged())
                .getContent();

        List<RepairListItem> items = new ArrayList<>();
        for (RepairRequest req : requests) {
            String label = resolveLabel(req);
            items.add(new RepairListItem(
                    req.getReferenceNumber(),
                    req.getDeviceCategory().getName(),
                    label,
                    req.getSubmittedAt(),
                    req.getUpdatedAt()));
        }
        items.sort(Comparator.comparing(RepairListItem::updatedAt).reversed());
        return items;
    }

    public RepairDetail detail(String referenceNumber, UUID customerId) {
        RepairRequest req = requestRepo.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Repair not found"));

        if (!req.getCustomer().getId().equals(customerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }

        String label = resolveLabel(req);
        RepairDetail.AppointmentInfo appointment = null;

        // Check if there's a job (post-approval) with an appointment
        var jobOpt = jobRepo.findByRequestId(req.getId());
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            label = JOB_LABELS.getOrDefault(job.getStatus().name(), job.getStatus().name());
            // Appointment info pulled from audit/job — simplified for V1
            // Full appointment integration happens in SCH-001+
        }

        // Build status history from audit events
        List<RepairDetail.StatusHistoryItem> history = new ArrayList<>();
        history.addAll(
                auditRepo.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("REQUEST", req.getId())
                        .stream()
                        .map(e -> new RepairDetail.StatusHistoryItem(
                                e.getAction(), null, e.getCreatedAt()))
                        .toList());
        jobOpt.ifPresent(job ->
                history.addAll(
                        auditRepo.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("JOB", job.getId())
                                .stream()
                                .map(e -> new RepairDetail.StatusHistoryItem(
                                        e.getAction(), null, e.getCreatedAt()))
                                .toList()));
        history.sort(Comparator.comparing(RepairDetail.StatusHistoryItem::occurredAt));

        return new RepairDetail(
                req.getReferenceNumber(),
                req.getDeviceCategory().getName(),
                req.getDeviceBrand(),
                req.getDeviceModel(),
                req.getIssueDescription(),
                label,
                appointment,
                req.getStatus() == RequestStatus.REJECTED ? req.getCustomerFacingMessage() : null,
                req.getStatus() == RequestStatus.INFORMATION_REQUIRED
                        ? req.getInfoRequestedMessage() : null,
                history,
                req.getSubmittedAt(),
                req.getUpdatedAt());
    }

    private String resolveLabel(RepairRequest req) {
        if (req.getStatus() == RequestStatus.APPROVED) {
            // Check job status for a more specific label
            return jobRepo.findByRequestId(req.getId())
                    .map(j -> JOB_LABELS.getOrDefault(j.getStatus().name(), "In Progress"))
                    .orElse(REQUEST_LABELS.get("APPROVED"));
        }
        return REQUEST_LABELS.getOrDefault(req.getStatus().name(), req.getStatus().name());
    }
}
