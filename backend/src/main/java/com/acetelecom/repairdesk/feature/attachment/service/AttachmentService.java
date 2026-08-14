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
        String resolvedType = resolveAndValidate(file, ownerType, ownerId);

        String ext = extensionFor(resolvedType);
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
        att.setContentType(resolvedType);
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

    /** Opened attachment stream plus metadata for Content-Type on download. */
    public record OpenedAttachment(Attachment attachment, InputStream stream) {}

    @Transactional(readOnly = true)
    public OpenedAttachment streamById(UUID attachmentId, AuthenticatedUser actor) {
        Attachment att = attachmentRepo.findById(attachmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Attachment not found"));
        return openStream(att, actor);
    }

    @Transactional(readOnly = true)
    public OpenedAttachment stream(String storageKey, AuthenticatedUser actor) {
        // Clients may URI-encode keys (e.g. uuid.jpg); normalize before lookup.
        String key = storageKey == null ? "" : java.net.URLDecoder.decode(
                storageKey, java.nio.charset.StandardCharsets.UTF_8);
        Attachment att = attachmentRepo.findByStorageKey(key)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Attachment not found"));
        return openStream(att, actor);
    }

    private OpenedAttachment openStream(Attachment att, AuthenticatedUser actor) {
        // Customers can only access attachments on their own requests
        if ("CUSTOMER".equals(actor.role())) {
            if (!"REPAIR_REQUEST".equals(att.getOwnerType())) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Access denied to this attachment");
            }
            requestRepo.findById(att.getOwnerId()).ifPresent(req -> {
                if (!req.getCustomer().getId().equals(actor.id())) {
                    throw new ApiException(ErrorCode.FORBIDDEN,
                            "Access denied to this attachment");
                }
            });
        }

        try {
            return new OpenedAttachment(att, storageService.load(att.getStorageKey()));
        } catch (java.io.FileNotFoundException e) {
            // Ephemeral container disks lose files across restarts — surface as
            // NOT_FOUND so clients show a clear re-upload message.
            throw new ApiException(ErrorCode.NOT_FOUND,
                    "Photo file is missing from storage. Please upload it again.");
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

    /**
     * Validates limits + magic bytes and returns a canonical stored content type
     * (never {@code application/octet-stream}). Mobile camera uploads often omit
     * or mis-declare the part MIME type.
     */
    private String resolveAndValidate(MultipartFile file, String ownerType, UUID ownerId) {
        if (file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File is empty");
        }

        byte[] header = readHeader(file, 12);
        String declared = normalizeDeclaredType(file.getContentType());
        String sniffed = sniffImageType(header);

        String contentType;
        if (declared != null && ALLOWED_TYPES.contains(declared)) {
            contentType = "image/jpg".equals(declared) ? "image/jpeg" : declared;
            if (contentType.startsWith("image/hei")) {
                // HEIC/HEIF: allow declaration; magic check skipped.
            } else if (magicMatches(contentType, header)) {
                // Declared type matches file bytes.
            } else if (sniffed != null) {
                // Wrong/missing client MIME (common on mobile camera) — trust magic.
                contentType = sniffed;
            } else {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "File content does not match declared type " + contentType);
            }
        } else if (sniffed != null) {
            // Camera / Dio often sends application/octet-stream — accept via magic.
            contentType = sniffed;
        } else {
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
        return contentType;
    }

    private static String normalizeDeclaredType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        // Strip "; charset=..." if present
        String type = contentType.split(";")[0].trim().toLowerCase();
        if ("application/octet-stream".equals(type) || "binary/octet-stream".equals(type)) {
            return null;
        }
        return type;
    }

    private static byte[] readHeader(MultipartFile file, int length) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[length];
            int read = in.read(header);
            if (read < 4) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "File too small to validate");
            }
            if (read < length) {
                byte[] trimmed = new byte[read];
                System.arraycopy(header, 0, trimmed, 0, read);
                return trimmed;
            }
            return header;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Could not read file for validation");
        }
    }

    /** SEC-002 — sniff common image signatures from the first bytes. */
    private static String sniffImageType(byte[] header) {
        if (header.length >= 3
                && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (header.length >= 4
                && header[0] == (byte) 0x89 && header[1] == 0x50
                && header[2] == 0x4E && header[3] == 0x47) {
            return "image/png";
        }
        if (header.length >= 3
                && header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46) {
            return "image/gif";
        }
        // RIFF....WEBP — "WEBP" is at bytes 8–11, not 4–7
        if (header.length >= 12
                && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
            return "image/webp";
        }
        return null;
    }

    private static boolean magicMatches(String contentType, byte[] header) {
        String sniffed = sniffImageType(header);
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "image/jpeg".equals(sniffed);
            case "image/png" -> "image/png".equals(sniffed);
            case "image/gif" -> "image/gif".equals(sniffed);
            case "image/webp" -> "image/webp".equals(sniffed);
            case "image/heic", "image/heif" -> true; // ftyp box; skip strict check
            default -> false;
        };
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
