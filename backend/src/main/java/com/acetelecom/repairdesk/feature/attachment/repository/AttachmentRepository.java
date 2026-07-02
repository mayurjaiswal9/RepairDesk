package com.acetelecom.repairdesk.feature.attachment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.attachment.domain.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(String ownerType, UUID ownerId);

    long countByOwnerTypeAndOwnerId(String ownerType, UUID ownerId);

    Optional<Attachment> findByStorageKey(String storageKey);
}
