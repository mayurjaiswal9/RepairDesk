package com.acetelecom.repairdesk.feature.notification.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.notification.domain.Notification;
import com.acetelecom.repairdesk.feature.notification.dto.NotificationResponse;
import com.acetelecom.repairdesk.feature.notification.repository.NotificationRepository;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@Service
@Transactional
public class NotificationService {

    private static final int INBOX_LIMIT = 30;

    private final NotificationRepository notifRepo;
    private final UserRepository userRepo;

    public NotificationService(NotificationRepository notifRepo, UserRepository userRepo) {
        this.notifRepo = notifRepo;
        this.userRepo = userRepo;
    }

    /**
     * Creates a notification for a recipient.
     *
     * @param relatedType {@code REPAIR_REQUEST}, {@code JOB}, or {@code APPOINTMENT}
     */
    public void send(UUID recipientId, String type, String message,
            String referenceNumber, String relatedType, UUID relatedEntityId) {
        User recipient = userRepo.findById(recipientId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Recipient not found"));
        Notification n = new Notification();
        n.setCompanyId(CompanyContext.get() != null ? CompanyContext.get() : recipient.getCompanyId());
        n.setRecipient(recipient);
        n.setType(type);
        n.setMessage(message);
        n.setReferenceNumber(referenceNumber);
        n.setRelatedType(relatedType);
        n.setRelatedEntityId(relatedEntityId);
        notifRepo.save(n);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> inbox(UUID recipientId) {
        return notifRepo.findByRecipientIdOrderByCreatedAtDesc(
                        recipientId, PageRequest.of(0, INBOX_LIMIT))
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return notifRepo.countByRecipientIdAndReadFalse(recipientId);
    }

    public void markRead(UUID notificationId, UUID recipientId) {
        Notification n = notifRepo.findById(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Notification not found"));
        CompanyContext.requireSame(n.getCompanyId(), "Notification not found");
        if (!n.getRecipient().getId().equals(recipientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }
        n.setRead(true);
        notifRepo.save(n);
    }

    public void markAllRead(UUID recipientId) {
        notifRepo.markAllRead(recipientId);
    }
}
