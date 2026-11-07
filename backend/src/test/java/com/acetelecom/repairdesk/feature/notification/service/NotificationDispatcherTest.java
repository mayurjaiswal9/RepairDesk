package com.acetelecom.repairdesk.feature.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.notification.config.PushProperties;
import com.acetelecom.repairdesk.feature.notification.domain.Notification;
import com.acetelecom.repairdesk.feature.notification.domain.NotificationEvent;
import com.acetelecom.repairdesk.feature.notification.domain.NotificationRule;
import com.acetelecom.repairdesk.feature.notification.dto.NotificationRuleRequest;
import com.acetelecom.repairdesk.feature.notification.repository.NotificationRuleRepository;
import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock private NotificationRuleRepository ruleRepo;
    @Mock private NotificationService inbox;
    @Mock private UserRepository userRepo;
    @Mock private PhonePushSender phone;
    @Mock private DesktopPushSender desktop;

    private NotificationRuleService rules;
    private NotificationDispatcher dispatcher;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        CompanyContext.set(companyId);
        rules = new NotificationRuleService(ruleRepo, userRepo, new PushProperties());
        dispatcher = new NotificationDispatcher(rules, inbox, userRepo, phone, desktop);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void disabledRuleSendsNothing() {
        NotificationRule rule = NotificationEvent.JOB_COMPLETED.newRule(companyId);
        rule.setEnabled(false);
        when(ruleRepo.findByCompanyIdOrderByEventKeyAsc(companyId)).thenReturn(List.of(rule));
        when(ruleRepo.findByCompanyIdAndEventKey(companyId, "JOB_COMPLETED")).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        dispatcher.publish(NotificationEvent.JOB_COMPLETED, NotificationContext.create()
                .customer(UUID.randomUUID())
                .reference("RD-1")
                .job(UUID.randomUUID()));

        verify(inbox, never()).send(any(), any(), any(), any(), any(), any());
        verify(phone, never()).send(any(), any(), any(), any());
    }

    @Test
    void customerUpdateIsSavedAndAlerted() {
        NotificationRule rule = NotificationEvent.JOB_COMPLETED.newRule(companyId);
        UUID customerId = UUID.randomUUID();
        User customer = user(customerId, Role.CUSTOMER);
        Notification saved = new Notification();
        saved.setId(UUID.randomUUID());
        when(ruleRepo.findByCompanyIdOrderByEventKeyAsc(companyId)).thenReturn(List.of(rule));
        when(ruleRepo.findByCompanyIdAndEventKey(companyId, "JOB_COMPLETED")).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepo.findById(customerId)).thenReturn(Optional.of(customer));
        when(inbox.send(any(), any(), any(), any(), any(), any())).thenReturn(saved);

        dispatcher.publish(NotificationEvent.JOB_COMPLETED, NotificationContext.create()
                .customer(customerId)
                .reference("RD-1")
                .job(UUID.randomUUID()));

        verify(inbox).send(eq(customerId), eq("JOB_COMPLETED"),
                eq("Your repair RD-1 is complete and ready for collection."),
                eq("RD-1"), eq("JOB"), any());
        verify(phone).send(eq(customerId), eq("Ace Smart Care"),
                org.mockito.ArgumentMatchers.contains("RD-1"), any());
    }

    @Test
    void officeRequestGoesToAdminAndStaff() {
        NotificationRule rule = NotificationEvent.REQUEST_SUBMITTED.newRule(companyId);
        User admin = user(UUID.randomUUID(), Role.ADMIN);
        User staff = user(UUID.randomUUID(), Role.STAFF);
        Notification saved = new Notification();
        saved.setId(UUID.randomUUID());
        when(ruleRepo.findByCompanyIdOrderByEventKeyAsc(companyId)).thenReturn(List.of(rule));
        when(ruleRepo.findByCompanyIdAndEventKey(companyId, "REQUEST_SUBMITTED")).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepo.findActiveByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(userRepo.findActiveByRole(Role.STAFF)).thenReturn(List.of(staff));
        when(inbox.send(any(), any(), any(), any(), any(), any())).thenReturn(saved);

        dispatcher.publish(NotificationEvent.REQUEST_SUBMITTED, NotificationContext.create()
                .reference("RD-9")
                .request(UUID.randomUUID()));

        verify(inbox).send(eq(admin.getId()), eq("REQUEST_SUBMITTED"),
                eq("New repair request RD-9 was submitted."), eq("RD-9"), eq("REPAIR_REQUEST"), any());
        verify(inbox).send(eq(staff.getId()), eq("REQUEST_SUBMITTED"),
                eq("New repair request RD-9 was submitted."), eq("RD-9"), eq("REPAIR_REQUEST"), any());
        verify(desktop).send(eq(admin.getId()), eq("RepairDesk"),
                org.mockito.ArgumentMatchers.contains("RD-9"),
                org.mockito.ArgumentMatchers.startsWith("/requests/"),
                any());
    }

    @Test
    void whatsAppAndSmsCannotBeSavedOn() {
        when(ruleRepo.findByCompanyIdOrderByEventKeyAsc(companyId)).thenReturn(List.of());
        when(ruleRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> rules.replace(List.of(new NotificationRuleRequest(
                "JOB_COMPLETED", true, "Done {reference}",
                true, false, false, false, List.of(),
                true, true, false, true, false))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("WhatsApp and SMS");
    }

    @Test
    void renderFillsTheJobNumber() {
        assertThat(NotificationDispatcher.render("Job {reference} at {time}",
                NotificationContext.create().reference("RD-2").time("10:00")))
                .isEqualTo("Job RD-2 at 10:00");
    }

    private User user(UUID id, Role role) {
        User user = new User();
        user.setId(id);
        user.setCompanyId(companyId);
        user.setUsername(role.name().toLowerCase());
        user.setName(role.name());
        user.setMobile("9000000000");
        user.setPasswordHash("x");
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }
}
