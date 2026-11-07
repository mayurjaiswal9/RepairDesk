package com.acetelecom.repairdesk.feature.notification.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.notification.domain.DeviceToken;
import com.acetelecom.repairdesk.feature.notification.domain.WebPushSubscription;
import com.acetelecom.repairdesk.feature.notification.repository.DeviceTokenRepository;
import com.acetelecom.repairdesk.feature.notification.repository.WebPushSubscriptionRepository;

@Service
@Transactional
public class PushDeviceService {

    private final DeviceTokenRepository deviceTokens;
    private final WebPushSubscriptionRepository webPush;

    public PushDeviceService(DeviceTokenRepository deviceTokens, WebPushSubscriptionRepository webPush) {
        this.deviceTokens = deviceTokens;
        this.webPush = webPush;
    }

    public void registerPhone(UUID userId, String token, String platform) {
        String cleaned = requireToken(token, 512);
        DeviceToken row = deviceTokens.findByToken(cleaned).orElseGet(DeviceToken::new);
        row.setUserId(userId);
        row.setToken(cleaned);
        row.setPlatform(platform == null || platform.isBlank() ? "ANDROID" : platform.trim());
        row.setUpdatedAt(Instant.now());
        deviceTokens.save(row);
    }

    public void removePhone(UUID userId, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        deviceTokens.deleteByTokenAndUserId(token.trim(), userId);
    }

    public void registerBrowser(UUID userId, String endpoint, String p256dh, String auth) {
        if (endpoint == null || endpoint.isBlank() || p256dh == null || p256dh.isBlank()
                || auth == null || auth.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Browser subscription is incomplete");
        }
        if (p256dh.length() > 255 || auth.length() > 255) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Browser subscription is incomplete");
        }
        WebPushSubscription row = webPush.findByEndpoint(endpoint.trim()).orElseGet(WebPushSubscription::new);
        row.setUserId(userId);
        row.setEndpoint(endpoint.trim());
        row.setP256dh(p256dh.trim());
        row.setAuthKey(auth.trim());
        row.setUpdatedAt(Instant.now());
        webPush.save(row);
    }

    public void removeBrowser(UUID userId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        webPush.deleteByEndpointAndUserId(endpoint.trim(), userId);
    }

    private static String requireToken(String token, int max) {
        if (token == null || token.isBlank() || token.trim().length() > max) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Device token is required");
        }
        return token.trim();
    }
}
