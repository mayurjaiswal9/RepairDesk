package com.acetelecom.repairdesk.feature.tracking.dto;

import java.time.Instant;

/**
 * Customer Home list item — friendly status, no internal fields (TRACK-001).
 */
public record RepairListItem(
        String referenceNumber,
        String deviceCategoryName,
        String friendlyStatus,
        Instant submittedAt,
        Instant updatedAt) {
}
