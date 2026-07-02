package com.acetelecom.repairdesk.feature.tracking.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Customer repair detail — friendly view, no internal fields (TRACK-002).
 */
public record RepairDetail(
        String referenceNumber,
        String deviceCategoryName,
        String deviceBrand,
        String deviceModel,
        String issueDescription,
        String friendlyStatus,
        AppointmentInfo appointment,
        String rejectionMessage,
        String infoRequestedMessage,
        List<StatusHistoryItem> statusHistory,
        Instant submittedAt,
        Instant updatedAt) {

    public record AppointmentInfo(
            LocalDate scheduledDate,
            LocalTime startTime,
            String technicianName) {
    }

    public record StatusHistoryItem(
            String action,
            String detail,
            Instant occurredAt) {
    }
}
