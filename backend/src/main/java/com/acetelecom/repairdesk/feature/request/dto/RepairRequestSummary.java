package com.acetelecom.repairdesk.feature.request.dto;

import java.time.Instant;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.request.domain.Priority;
import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;

public record RepairRequestSummary(
        UUID id,
        String referenceNumber,
        String customerUsername,
        String deviceCategoryName,
        String contactName,
        String contactMobile,
        String deviceBrand,
        String deviceModel,
        RequestStatus status,
        Priority priority,
        Instant submittedAt,
        Instant updatedAt,
        UUID jobId,
        JobStatus jobStatus,
        String assignedTechnicianName) {

    /** @param job the Job created on approval (1:1), or null if not yet approved. */
    public static RepairRequestSummary from(RepairRequest r, Job job) {
        return new RepairRequestSummary(
                r.getId(),
                r.getReferenceNumber(),
                r.getCustomer().getUsername(),
                r.getDeviceCategory().getName(),
                r.getContactName(),
                r.getContactMobile(),
                r.getDeviceBrand(),
                r.getDeviceModel(),
                r.getStatus(),
                r.getPriority(),
                r.getSubmittedAt(),
                r.getUpdatedAt(),
                job != null ? job.getId() : null,
                job != null ? job.getStatus() : null,
                job != null && job.getAssignedTechnician() != null
                        ? job.getAssignedTechnician().getName() : null);
    }
}
