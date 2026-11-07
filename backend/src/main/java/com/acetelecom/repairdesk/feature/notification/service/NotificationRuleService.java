package com.acetelecom.repairdesk.feature.notification.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.notification.config.PushProperties;
import com.acetelecom.repairdesk.feature.notification.domain.NotificationEvent;
import com.acetelecom.repairdesk.feature.notification.domain.NotificationRule;
import com.acetelecom.repairdesk.feature.notification.dto.NotificationRuleRequest;
import com.acetelecom.repairdesk.feature.notification.dto.NotificationRuleResponse;
import com.acetelecom.repairdesk.feature.notification.dto.NotificationSettingsResponse;
import com.acetelecom.repairdesk.feature.notification.repository.NotificationRuleRepository;
import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@Service
@Transactional
public class NotificationRuleService {

    private final NotificationRuleRepository ruleRepo;
    private final UserRepository userRepo;
    private final PushProperties pushProperties;

    public NotificationRuleService(NotificationRuleRepository ruleRepo, UserRepository userRepo,
            PushProperties pushProperties) {
        this.ruleRepo = ruleRepo;
        this.userRepo = userRepo;
        this.pushProperties = pushProperties;
    }

    public NotificationSettingsResponse settings() {
        UUID companyId = CompanyContext.get();
        List<NotificationRule> rules = ensure(companyId);
        rules.sort(Comparator.comparingInt(rule -> eventOrder(rule.getEventKey())));
        List<NotificationSettingsResponse.Person> people = userRepo.findAllNonDeleted().stream()
                .filter(User::isActive)
                .filter(user -> user.getRole() != Role.CUSTOMER)
                .map(user -> new NotificationSettingsResponse.Person(
                        user.getId(),
                        user.getName() == null || user.getName().isBlank() ? user.getUsername() : user.getName(),
                        user.getRole().name()))
                .toList();
        return new NotificationSettingsResponse(
                rules.stream().map(NotificationRuleResponse::from).toList(),
                people,
                pushProperties.phoneConfigured(),
                pushProperties.desktopConfigured());
    }

    public NotificationSettingsResponse replace(List<NotificationRuleRequest> incoming) {
        UUID companyId = CompanyContext.get();
        ensure(companyId);
        if (incoming == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Notification rules are required");
        }
        Set<String> seen = new HashSet<>();
        for (NotificationRuleRequest request : incoming) {
            if (request.channelWhatsapp() || request.channelSms()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "WhatsApp and SMS stay off until those channels are connected");
            }
            NotificationEvent event = NotificationEvent.find(request.eventKey())
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Unknown notification"));
            if (!seen.add(event.name())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Duplicate notification");
            }
            if (request.messageTemplate() == null || request.messageTemplate().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Message text is required");
            }
            if (request.messageTemplate().length() > 500) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Message text is too long");
            }
            NotificationRule rule = ruleRepo.findByCompanyIdAndEventKey(companyId, event.name())
                    .orElseGet(() -> event.newRule(companyId));
            rule.setEnabled(request.enabled());
            rule.setMessageTemplate(request.messageTemplate().trim());
            rule.setNotifyCustomer(request.notifyCustomer());
            rule.setNotifyTechnician(request.notifyTechnician());
            rule.setNotifyStaff(request.notifyStaff());
            rule.setNotifyAdmin(request.notifyAdmin());
            rule.setChannelInApp(request.channelInApp());
            rule.setChannelPhone(request.channelPhone());
            rule.setChannelDesktop(request.channelDesktop());
            rule.setChannelWhatsapp(false);
            rule.setChannelSms(false);
            rule.setUpdatedAt(java.time.Instant.now());
            rule.getExtraUserIds().clear();
            for (UUID userId : request.extraUserIds() == null ? List.<UUID>of() : request.extraUserIds()) {
                User user = userRepo.findById(userId)
                        .filter(candidate -> candidate.isActive() && candidate.getRole() != Role.CUSTOMER)
                        .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                                "Choose an active staff member"));
                if (user.getStatus() == AccountStatus.DELETED) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR, "Choose an active staff member");
                }
                rule.getExtraUserIds().add(user.getId());
            }
            ruleRepo.save(rule);
        }
        return settings();
    }

    public NotificationRule require(UUID companyId, NotificationEvent event) {
        ensure(companyId);
        return ruleRepo.findByCompanyIdAndEventKey(companyId, event.name())
                .orElseGet(() -> event.newRule(companyId));
    }

    public List<NotificationRule> ensure(UUID companyId) {
        if (companyId == null) {
            return List.of();
        }
        List<NotificationRule> existing = new ArrayList<>(ruleRepo.findByCompanyIdOrderByEventKeyAsc(companyId));
        Set<String> have = new HashSet<>();
        for (NotificationRule rule : existing) {
            have.add(rule.getEventKey());
        }
        for (NotificationEvent event : NotificationEvent.values()) {
            if (!have.contains(event.name())) {
                existing.add(ruleRepo.save(event.newRule(companyId)));
            }
        }
        return existing;
    }

    private static int eventOrder(String key) {
        NotificationEvent[] values = NotificationEvent.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].name().equals(key)) {
                return i;
            }
        }
        return values.length;
    }
}
