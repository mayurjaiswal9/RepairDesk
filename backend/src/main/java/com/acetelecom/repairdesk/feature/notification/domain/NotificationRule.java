package com.acetelecom.repairdesk.feature.notification.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.common.domain.TenantEntity;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_rules")
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class NotificationRule extends TenantEntity {

    @Column(name = "event_key", nullable = false, length = 40)
    private String eventKey;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "message_template", nullable = false, columnDefinition = "text")
    private String messageTemplate;

    @Column(name = "notify_customer", nullable = false)
    private boolean notifyCustomer;

    @Column(name = "notify_technician", nullable = false)
    private boolean notifyTechnician;

    @Column(name = "notify_staff", nullable = false)
    private boolean notifyStaff;

    @Column(name = "notify_admin", nullable = false)
    private boolean notifyAdmin;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_rule_users", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "user_id", nullable = false)
    private Set<UUID> extraUserIds = new HashSet<>();

    @Column(name = "channel_in_app", nullable = false)
    private boolean channelInApp = true;

    @Column(name = "channel_phone", nullable = false)
    private boolean channelPhone;

    @Column(name = "channel_desktop", nullable = false)
    private boolean channelDesktop;

    @Column(name = "channel_whatsapp", nullable = false)
    private boolean channelWhatsapp;

    @Column(name = "channel_sms", nullable = false)
    private boolean channelSms;
}
