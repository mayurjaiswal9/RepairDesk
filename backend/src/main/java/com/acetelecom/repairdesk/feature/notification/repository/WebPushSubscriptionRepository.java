package com.acetelecom.repairdesk.feature.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.notification.domain.WebPushSubscription;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, UUID> {

    List<WebPushSubscription> findByUserId(UUID userId);

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    void deleteByEndpointAndUserId(String endpoint, UUID userId);
}
