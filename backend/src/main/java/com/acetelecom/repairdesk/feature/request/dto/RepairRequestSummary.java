package com.acetelecom.repairdesk.feature.request.dto;

import java.time.Instant;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.request.domain.Priority;
import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;

public record RepairRequestSummary(
        UUID id,
        String referenceNumber,
        String customerUsername,
        String deviceCategoryName,
        String contactName,
        RequestStatus status,
        Priority priority,
        Instant submittedAt,
        Instant updatedAt) {

    public static RepairRequestSummary from(RepairRequest r) {
        return new RepairRequestSummary(
                r.getId(),
                r.getReferenceNumber(),
                r.getCustomer().getUsername(),
                r.getDeviceCategory().getName(),
                r.getContactName(),
                r.getStatus(),
                r.getPriority(),
                r.getSubmittedAt(),
                r.getUpdatedAt());
    }
}
