package com.acetelecom.repairdesk.feature.attachment.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.attachment.domain.Attachment;
import com.acetelecom.repairdesk.feature.attachment.dto.AttachmentResponse;
import com.acetelecom.repairdesk.feature.attachment.repository.AttachmentRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

/**
 * Manages attachment lifecycle (FILE-001/002/003).
 *
 * <p>Limits per owner:
 * <ul>
 *   <li>REPAIR_REQUEST: max 5, each ≤ 10 MB, total ≤ 25 MB</li>
 *   <li>JOB: max 10 (internal only)</li>
 * </ul>
 */
@Service
@Transactional
public class AttachmentService {

    private static final int REQUEST_MAX_FILES = 5;
    private static final long REQUEST_MAX_SINGLE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final long REQUEST_MAX_TOTAL_BYTES = 25L * 1024 * 1024;  // 25 MB
    private static final int JOB_MAX_FILES = 10;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "image/webp", "image/heic", "image/heif");

    private final AttachmentRepository attachmentRepo;
    private final StorageService storageService;

    public AttachmentService(AttachmentRepository attachmentRepo,
            StorageService storageService) {
        this.attachmentRepo = attachmentRepo;
        this.storageService = storageService;
    }

    public AttachmentResponse upload(String ownerType, UUID ownerId,
            MultipartFile file, AuthenticatedUser actor) {
        validateFile(file, ownerType, ownerId);

        String ext = extensionFor(file.getContentType());
        String storageKey;
        try {
            storageKey = storageService.store(file.getInputStream(), ext);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "File storage failed: " + e.getMessage());
        }

        Attachment att = new Attachment();
        att.setOwnerType(ownerType);
        att.setOwnerId(ownerId);
        att.setUploadedBy(actor.id());
        att.setContentType(file.getContentType());
        att.setFileSizeBytes(file.getSize());
        att.setOriginalFilename(file.getOriginalFilename());
        att.setStorageKey(storageKey);
        attachmentRepo.save(att);

        return AttachmentResponse.from(att);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(String ownerType, UUID ownerId) {
        return attachmentRepo
                .findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(ownerType, ownerId)
                .stream().map(AttachmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InputStream stream(String storageKey, AuthenticatedUser actor) {
        Attachment att = attachmentRepo.findByStorageKey(storageKey)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Attachment not found"));

        // Customers can only access their own request attachments
        if ("CUSTOMER".equals(actor.role())) {
            if (!"REPAIR_REQUEST".equals(att.getOwnerType())) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Access denied to this attachment");
            }
        }

        try {
            return storageService.load(storageKey);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "File retrieval failed: " + e.getMessage());
        }
    }

    public void delete(UUID attachmentId, AuthenticatedUser actor) {
        Attachment att = attachmentRepo.findById(attachmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Attachment not found"));
        if (!"STAFF".equals(actor.role()) && !"ADMIN".equals(actor.role())) {
            if (!att.getUploadedBy().equals(actor.id())) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Cannot delete another user's attachment");
            }
        }
        storageService.delete(att.getStorageKey());
        attachmentRepo.delete(att);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file, String ownerType, UUID ownerId) {
        if (file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "File type not allowed. Accepted: JPEG, PNG, GIF, WEBP, HEIC");
        }

        long currentCount = attachmentRepo.countByOwnerTypeAndOwnerId(ownerType, ownerId);
        int maxFiles = "JOB".equals(ownerType) ? JOB_MAX_FILES : REQUEST_MAX_FILES;
        if (currentCount >= maxFiles) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Maximum " + maxFiles + " attachments allowed per " + ownerType.toLowerCase());
        }

        if ("REPAIR_REQUEST".equals(ownerType)) {
            if (file.getSize() > REQUEST_MAX_SINGLE_BYTES) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "File exceeds 10 MB limit");
            }
            long totalBytes = attachmentRepo
                    .findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(ownerType, ownerId)
                    .stream().mapToLong(Attachment::getFileSizeBytes).sum();
            if (totalBytes + file.getSize() > REQUEST_MAX_TOTAL_BYTES) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Total attachment size would exceed 25 MB limit");
            }
        }
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) return "bin";
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/heic", "image/heif" -> "heic";
            default -> "bin";
        };
    }
}
