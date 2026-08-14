package com.acetelecom.repairdesk.feature.job.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.request.domain.Priority;

public record JobResponse(
        UUID id,
        String referenceNumber,
        UUID requestId,
        UUID customerId,
        String customerUsername,
        String customerName,
        UUID deviceCategoryId,
        String deviceCategoryName,
        String contactName,
        String contactMobile,
        String deviceBrand,
        String deviceModel,
        String deviceSerial,
        LocalDate purchaseDate,
        boolean warrantyConfirmed,
        String issueDescription,
        JobStatus status,
        Priority priority,
        UUID assignedTechnicianId,
        String assignedTechnicianName,
        UUID currentAppointmentId,
        String resolutionSummary,
        String cancelReason,
        String internalNotes,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static JobResponse from(Job j) {
        return new JobResponse(
                j.getId(),
                j.getReferenceNumber(),
                j.getRequest().getId(),
                j.getCustomer().getId(),
                j.getCustomer().getUsername(),
                j.getCustomer().getName(),
                j.getDeviceCategory().getId(),
                j.getDeviceCategory().getName(),
                j.getContactName(),
                j.getContactMobile(),
                j.getDeviceBrand(),
                j.getDeviceModel(),
                j.getDeviceSerial(),
                j.getPurchaseDate(),
                j.isWarrantyConfirmed(),
                j.getIssueDescription(),
                j.getStatus(),
                j.getPriority(),
                j.getAssignedTechnician() != null ? j.getAssignedTechnician().getId() : null,
                j.getAssignedTechnician() != null ? j.getAssignedTechnician().getName() : null,
                j.getCurrentAppointmentId(),
                j.getResolutionSummary(),
                j.getCancelReason(),
                j.getInternalNotes(),
                j.getCreatedAt(),
                j.getUpdatedAt(),
                j.getVersion());
    }
}
