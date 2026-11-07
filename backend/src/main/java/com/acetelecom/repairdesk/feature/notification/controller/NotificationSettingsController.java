package com.acetelecom.repairdesk.feature.notification.controller;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.notification.dto.NotificationSettingsResponse;
import com.acetelecom.repairdesk.feature.notification.dto.UpdateNotificationRulesRequest;
import com.acetelecom.repairdesk.feature.notification.service.NotificationRuleService;

@RestController
@RequestMapping("/settings/notifications")
public class NotificationSettingsController {

    private final NotificationRuleService rules;

    public NotificationSettingsController(NotificationRuleService rules) {
        this.rules = rules;
    }

    @GetMapping
    @Secured("ROLE_ADMIN")
    public NotificationSettingsResponse get() {
        return rules.settings();
    }

    @PutMapping
    @Secured("ROLE_ADMIN")
    public NotificationSettingsResponse save(@RequestBody UpdateNotificationRulesRequest body) {
        return rules.replace(body == null ? null : body.rules());
    }
}
