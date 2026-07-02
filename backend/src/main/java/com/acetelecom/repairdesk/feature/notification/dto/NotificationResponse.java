package com.acetelecom.repairdesk.feature.notification.dto;

import java.time.Instant;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.notification.domain.Notification;

public record NotificationResponse(
        UUID id,
        String type,
        String message,
        String referenceNumber,
        UUID relatedEntityId,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getMessage(),
                n.getReferenceNumber(), n.getRelatedEntityId(),
                n.isRead(), n.getCreatedAt());
    }
}
