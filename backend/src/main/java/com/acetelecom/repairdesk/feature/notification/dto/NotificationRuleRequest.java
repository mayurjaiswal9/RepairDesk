package com.acetelecom.repairdesk.feature.notification.dto;

import java.util.List;
import java.util.UUID;

public record NotificationRuleRequest(
        String eventKey,
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
}
