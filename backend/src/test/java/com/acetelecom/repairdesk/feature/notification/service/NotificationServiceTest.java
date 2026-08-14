package com.acetelecom.repairdesk.feature.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.notification.domain.Notification;
import com.acetelecom.repairdesk.feature.notification.repository.NotificationRepository;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

/** TEST-006: Notification rules. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notifRepo;
    @Mock
    private UserRepository userRepo;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notifRepo, userRepo);
    }

    @Test
    void send_persistsNotificationWithCorrectFields() {
        UUID recipientId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();
        User recipient = buildUser(recipientId);
        when(userRepo.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(notifRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.send(recipientId, "JOB_APPROVED",
                "Your repair was approved", "RD-2026000001", "JOB", relatedId);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notifRepo).save(cap.capture());
        Notification saved = cap.getValue();
        assertThat(saved.getType()).isEqualTo("JOB_APPROVED");
        assertThat(saved.getMessage()).isEqualTo("Your repair was approved");
        assertThat(saved.getReferenceNumber()).isEqualTo("RD-2026000001");
        assertThat(saved.getRelatedType()).isEqualTo("JOB");
        assertThat(saved.getRelatedEntityId()).isEqualTo(relatedId);
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void send_recipientNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.send(
                unknownId, "TYPE", "msg", "REF", "JOB", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Recipient not found");
    }

    @Test
    void markRead_notOwner_throwsForbidden() {
        UUID notifId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        Notification n = new Notification();
        n.setId(notifId);
        User owner = buildUser(ownerId);
        n.setRecipient(owner);
        when(notifRepo.findById(notifId)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markRead(notifId, otherId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void markRead_owner_marksAsRead() {
        UUID notifId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Notification n = new Notification();
        n.setId(notifId);
        User owner = buildUser(ownerId);
        n.setRecipient(owner);
        when(notifRepo.findById(notifId)).thenReturn(Optional.of(n));
        when(notifRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markRead(notifId, ownerId);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    void unreadCount_delegatesToRepo() {
        UUID recipientId = UUID.randomUUID();
        when(notifRepo.countByRecipientIdAndReadFalse(recipientId)).thenReturn(5L);

        long count = notificationService.unreadCount(recipientId);

        assertThat(count).isEqualTo(5L);
    }

    private User buildUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setRole(Role.CUSTOMER);
        return u;
    }
}
