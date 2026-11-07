package com.acetelecom.repairdesk.feature.notification.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.notification.domain.NotificationEvent;
import com.acetelecom.repairdesk.feature.notification.domain.NotificationRule;

public record NotificationRuleResponse(
        String eventKey,
        String label,
        String description,
        boolean enabled,
        String messageTemplate,
        boolean notifyCustomer,
        boolean notifyTechnician,
        boolean notifyStaff,
        boolean notifyAdmin,
        List<UUID> extraUserIds,
        boolean channelInApp,
        boolean channelPhone,
        boolean channelDesktop,
        boolean channelWhatsapp,
        boolean channelSms) {

    public static NotificationRuleResponse from(NotificationRule rule) {
        NotificationEvent event = NotificationEvent.find(rule.getEventKey()).orElse(null);
        Set<UUID> extras = rule.getExtraUserIds() == null ? Set.of() : rule.getExtraUserIds();
        return new NotificationRuleResponse(
                rule.getEventKey(),
                event == null ? rule.getEventKey() : event.label(),
                event == null ? "" : event.description(),
                rule.isEnabled(),
                rule.getMessageTemplate(),
                rule.isNotifyCustomer(),
                rule.isNotifyTechnician(),
                rule.isNotifyStaff(),
                rule.isNotifyAdmin(),
                extras.stream().sorted().toList(),
                rule.isChannelInApp(),
                rule.isChannelPhone(),
                rule.isChannelDesktop(),
                false,
                false);
    }
}
