package com.acetelecom.repairdesk.feature.notification.dto;

import java.util.List;
import java.util.UUID;

public record NotificationSettingsResponse(
        List<NotificationRuleResponse> rules,
        List<Person> people,
        boolean phoneDeliveryConfigured,
        boolean desktopPushConfigured) {

    public record Person(UUID id, String name, String role) {
    }
}
