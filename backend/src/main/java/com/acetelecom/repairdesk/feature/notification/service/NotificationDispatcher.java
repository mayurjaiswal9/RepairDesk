package com.acetelecom.repairdesk.feature.notification.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.notification.domain.Notification;
import com.acetelecom.repairdesk.feature.notification.domain.NotificationEvent;
import com.acetelecom.repairdesk.feature.notification.domain.NotificationRule;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRuleService rules;
    private final NotificationService inbox;
    private final UserRepository userRepo;
    private final PhonePushSender phone;
    private final DesktopPushSender desktop;

    public NotificationDispatcher(NotificationRuleService rules, NotificationService inbox,
            UserRepository userRepo, PhonePushSender phone, DesktopPushSender desktop) {
        this.rules = rules;
        this.inbox = inbox;
        this.userRepo = userRepo;
        this.phone = phone;
        this.desktop = desktop;
    }

    public void publish(NotificationEvent event, NotificationContext ctx) {
        NotificationContext context = ctx == null ? NotificationContext.create() : ctx;
        UUID companyId = companyOf(context);
        if (companyId == null) {
            return;
        }
        NotificationRule rule = rules.require(companyId, event);
        if (!rule.isEnabled()) {
            return;
        }
        String message = render(rule.getMessageTemplate(), context);
        if (message.isBlank()) {
            return;
        }
        List<Pending> pending = new ArrayList<>();
        for (User recipient : recipients(rule, context)) {
            String notificationId = null;
            if (rule.isChannelInApp() || rule.isChannelDesktop()) {
                Notification saved = inbox.send(recipient.getId(), event.name(), message,
                        context.reference(), context.relatedType(), context.relatedId());
                notificationId = saved.getId() == null ? null : saved.getId().toString();
            }
            if (rule.isChannelPhone() || rule.isChannelDesktop()) {
                pending.add(new Pending(recipient.getId(), notificationId, message,
                        rule.isChannelPhone(), rule.isChannelDesktop()));
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        String path = DesktopPushSender.pathFor(context.relatedType(), context.relatedId());
        Map<String, String> data = data(event, context);
        Runnable deliver = () -> {
            for (Pending item : pending) {
                try {
                    if (item.phone()) {
                        phone.send(item.userId(), "Ace Smart Care", item.message(), data);
                    }
                    if (item.desktop()) {
                        desktop.send(item.userId(), "RepairDesk", item.message(), path, item.notificationId());
                    }
                } catch (RuntimeException ex) {
                    log.warn("Alert delivery failed");
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver.run();
                }
            });
        } else {
            deliver.run();
        }
    }

    private UUID companyOf(NotificationContext ctx) {
        if (CompanyContext.get() != null) {
            return CompanyContext.get();
        }
        UUID personId = ctx.technicianId() != null ? ctx.technicianId() : ctx.customerId();
        if (personId == null) {
            return null;
        }
        return userRepo.findById(personId).map(User::getCompanyId).orElse(null);
    }

    private List<User> recipients(NotificationRule rule, NotificationContext ctx) {
        Map<UUID, User> people = new LinkedHashMap<>();
        if (rule.isNotifyCustomer()) {
            add(people, ctx.customerId());
        }
        if (rule.isNotifyTechnician()) {
            add(people, ctx.technicianId());
        }
        if (rule.isNotifyStaff()) {
            addRole(people, Role.STAFF);
        }
        if (rule.isNotifyAdmin()) {
            addRole(people, Role.ADMIN);
        }
        if (rule.getExtraUserIds() != null) {
            for (UUID extra : rule.getExtraUserIds()) {
                add(people, extra);
            }
        }
        return List.copyOf(people.values());
    }

    private void addRole(Map<UUID, User> people, Role role) {
        for (User user : userRepo.findActiveByRole(role)) {
            if (user.isActive()) {
                people.putIfAbsent(user.getId(), user);
            }
        }
    }

    private void add(Map<UUID, User> people, UUID userId) {
        if (userId == null || people.containsKey(userId)) {
            return;
        }
        userRepo.findById(userId).filter(User::isActive).ifPresent(user -> people.put(user.getId(), user));
    }

    static String render(String template, NotificationContext ctx) {
        String text = template == null ? "" : template;
        text = text.replace("{reference}", empty(ctx.reference()));
        text = text.replace("{date}", empty(ctx.date()));
        text = text.replace("{time}", empty(ctx.time()));
        text = text.replace("{reason}", empty(ctx.reason()));
        text = text.replace("{message}", empty(ctx.detail()));
        text = text.replace("{when}", empty(ctx.when()));
        return text.trim();
    }

    private static Map<String, String> data(NotificationEvent event, NotificationContext ctx) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", event.name());
        data.put("relatedType", empty(ctx.relatedType()));
        data.put("relatedId", ctx.relatedId() == null ? "" : ctx.relatedId().toString());
        data.put("reference", empty(ctx.reference()));
        return data;
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    private record Pending(UUID userId, String notificationId, String message, boolean phone, boolean desktop) {
    }
}
