package com.acetelecom.repairdesk.feature.notification.dto;

import java.util.List;

public record UpdateNotificationRulesRequest(List<NotificationRuleRequest> rules) {
}
