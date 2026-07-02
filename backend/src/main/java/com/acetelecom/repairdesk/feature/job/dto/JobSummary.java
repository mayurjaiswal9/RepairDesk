package com.acetelecom.repairdesk.feature.job.dto;

import java.time.Instant;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.request.domain.Priority;

public record JobSummary(
        UUID id,
        String referenceNumber,
        String customerUsername,
        String deviceCategoryName,
        String contactName,
        JobStatus status,
        Priority priority,
        String assignedTechnicianName,
        Instant updatedAt) {

    public static JobSummary from(Job j) {
        return new JobSummary(
                j.getId(),
                j.getReferenceNumber(),
                j.getCustomer().getUsername(),
                j.getDeviceCategory().getName(),
                j.getContactName(),
                j.getStatus(),
                j.getPriority(),
                j.getAssignedTechnician() != null ? j.getAssignedTechnician().getName() : null,
                j.getUpdatedAt());
    }
}
