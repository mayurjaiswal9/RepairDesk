package com.acetelecom.repairdesk.feature.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.notification.domain.NotificationRule;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, UUID> {

    List<NotificationRule> findByCompanyIdOrderByEventKeyAsc(UUID companyId);

    Optional<NotificationRule> findByCompanyIdAndEventKey(UUID companyId, String eventKey);
}
