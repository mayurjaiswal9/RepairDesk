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
        String customerName,
        String deviceCategoryName,
        String contactName,
        JobStatus status,
        Priority priority,
        UUID assignedTechnicianId,
        String assignedTechnicianName,
        Instant updatedAt,
        String serviceCity,
        String serviceLandmark) {

    public static JobSummary from(Job j) {
        return new JobSummary(
                j.getId(),
                j.getReferenceNumber(),
                j.getCustomer().getUsername(),
                j.getCustomer().getName(),
                j.getDeviceCategory().getName(),
                j.getContactName(),
                j.getStatus(),
                j.getPriority(),
                j.getAssignedTechnician() != null ? j.getAssignedTechnician().getId() : null,
                j.getAssignedTechnician() != null ? j.getAssignedTechnician().getName() : null,
                j.getUpdatedAt(),
                j.getServiceLocation() == null ? null : j.getServiceLocation().getCity(),
                j.getServiceLocation() == null ? null : j.getServiceLocation().getLandmark());
    }
}
