package com.acetelecom.repairdesk.feature.request.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.request.domain.Priority;
import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;
import com.acetelecom.repairdesk.feature.request.domain.WarrantyStatus;

public record RepairRequestResponse(
        UUID id,
        String referenceNumber,
        UUID customerId,
        String customerUsername,
        UUID deviceCategoryId,
        String deviceCategoryName,
        String contactName,
        String contactMobile,
        String contactEmail,
        String deviceBrand,
        String deviceModel,
        String deviceSerial,
        LocalDate purchaseDate,
        WarrantyStatus warrantyStatus,
        String issueDescription,
        LocalDate preferredDate,
        LocalTime preferredTime,
        RequestStatus status,
        Priority priority,
        String rejectionReasonCode,
        String customerFacingMessage,
        String infoRequestedMessage,
        String customerInfoResponse,
        Instant submittedAt,
        Instant updatedAt,
        long version,
        UUID jobId,
        JobStatus jobStatus,
        String assignedTechnicianName) {

    /** @param job the Job created on approval (1:1), or null if not yet approved. */
    public static RepairRequestResponse from(RepairRequest r, Job job) {
        return new RepairRequestResponse(
                r.getId(),
                r.getReferenceNumber(),
                r.getCustomer().getId(),
                r.getCustomer().getUsername(),
                r.getDeviceCategory().getId(),
                r.getDeviceCategory().getName(),
                r.getContactName(),
                r.getContactMobile(),
                r.getContactEmail(),
                r.getDeviceBrand(),
                r.getDeviceModel(),
                r.getDeviceSerial(),
                r.getPurchaseDate(),
                r.getWarrantyStatus(),
                r.getIssueDescription(),
                r.getPreferredDate(),
                r.getPreferredTime(),
                r.getStatus(),
                r.getPriority(),
                r.getRejectionReasonCode(),
                r.getCustomerFacingMessage(),
                r.getInfoRequestedMessage(),
                r.getCustomerInfoResponse(),
                r.getSubmittedAt(),
                r.getUpdatedAt(),
                r.getVersion(),
                job != null ? job.getId() : null,
                job != null ? job.getStatus() : null,
                job != null && job.getAssignedTechnician() != null
                        ? job.getAssignedTechnician().getName() : null);
    }
}
