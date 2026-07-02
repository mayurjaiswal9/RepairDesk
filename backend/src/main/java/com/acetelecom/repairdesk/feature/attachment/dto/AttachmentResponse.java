package com.acetelecom.repairdesk.feature.attachment.dto;

import java.time.Instant;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.attachment.domain.Attachment;

public record AttachmentResponse(
        UUID id,
        String ownerType,
        UUID ownerId,
        String contentType,
        long fileSizeBytes,
        String originalFilename,
        String storageKey,
        Instant createdAt) {

    public static AttachmentResponse from(Attachment a) {
        return new AttachmentResponse(
                a.getId(),
                a.getOwnerType(),
                a.getOwnerId(),
                a.getContentType(),
                a.getFileSizeBytes(),
                a.getOriginalFilename(),
                a.getStorageKey(),
                a.getCreatedAt());
    }
}
