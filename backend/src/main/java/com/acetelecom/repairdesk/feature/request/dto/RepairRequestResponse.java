package com.acetelecom.repairdesk.feature.request.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

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
        Instant submittedAt,
        Instant updatedAt,
        long version) {

    public static RepairRequestResponse from(RepairRequest r) {
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
                r.getSubmittedAt(),
                r.getUpdatedAt(),
                r.getVersion());
    }
}
