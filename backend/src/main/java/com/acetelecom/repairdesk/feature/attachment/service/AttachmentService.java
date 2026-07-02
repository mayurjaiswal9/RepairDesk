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
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;
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
    private final RepairRequestRepository requestRepo;

    public AttachmentService(AttachmentRepository attachmentRepo,
            StorageService storageService,
            RepairRequestRepository requestRepo) {
        this.attachmentRepo = attachmentRepo;
        this.storageService = storageService;
        this.requestRepo = requestRepo;
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

        // Customers can only access attachments on their own requests
        if ("CUSTOMER".equals(actor.role())) {
            if (!"REPAIR_REQUEST".equals(att.getOwnerType())) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Access denied to this attachment");
            }
            // Verify the request belongs to this customer
            requestRepo.findById(att.getOwnerId()).ifPresent(req -> {
                if (!req.getCustomer().getId().equals(actor.id())) {
                    throw new ApiException(ErrorCode.FORBIDDEN,
                            "Access denied to this attachment");
                }
            });
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

        // Validate Content-Type header (primary check — trusts client declaration)
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "File type not allowed. Accepted: JPEG, PNG, GIF, WEBP, HEIC");
        }

        // Validate magic bytes (SEC-002): first few bytes must match image signatures
        validateMagicBytes(file, contentType);

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

    /**
     * Checks image magic bytes to prevent disguised executable uploads (SEC-002).
     * Only checks the first 8 bytes — sufficient for common image formats.
     */
    private void validateMagicBytes(MultipartFile file, String contentType) {
        try {
            byte[] header = new byte[8];
            int read = file.getInputStream().read(header);
            if (read < 4) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "File too small to validate");
            }
            boolean valid = switch (contentType.toLowerCase()) {
                case "image/jpeg", "image/jpg" ->
                        header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
                case "image/png" ->
                        header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
                case "image/gif" ->
                        header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46;
                case "image/webp" ->
                        read >= 8 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                                && header[4] == 0x57 && header[5] == 0x45 && header[6] == 0x42 && header[7] == 0x50;
                // HEIC/HEIF: ftyp box starts at byte 4; skip magic check for now
                default -> true;
            };
            if (!valid) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "File content does not match declared type " + contentType);
            }
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Could not read file for validation");
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
