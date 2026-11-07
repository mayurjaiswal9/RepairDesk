package com.acetelecom.repairdesk.feature.notification.service;

import java.security.Security;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.feature.notification.config.PushProperties;
import com.acetelecom.repairdesk.feature.notification.domain.WebPushSubscription;
import com.acetelecom.repairdesk.feature.notification.repository.WebPushSubscriptionRepository;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@Service
public class DesktopPushSender {

    private static final Logger log = LoggerFactory.getLogger(DesktopPushSender.class);

    private final WebPushSubscriptionRepository subscriptions;
    private final PushProperties properties;
    private PushService pushService;
    private boolean unavailable;

    public DesktopPushSender(WebPushSubscriptionRepository subscriptions, PushProperties properties) {
        this.subscriptions = subscriptions;
        this.properties = properties;
    }

    @Transactional
    public void send(UUID userId, String title, String body, String url, String notificationId) {
        if (userId == null || !properties.desktopConfigured()) {
            return;
        }
        PushService service = service();
        if (service == null) {
            return;
        }
        byte[] payload = payload(title, body, url, notificationId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (WebPushSubscription subscription : List.copyOf(subscriptions.findByUserId(userId))) {
            try {
                Notification notification = new Notification(
                        subscription.getEndpoint(),
                        subscription.getP256dh(),
                        subscription.getAuthKey(),
                        payload);
                HttpResponse response = service.send(notification);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    subscriptions.delete(subscription);
                }
            } catch (Exception ex) {
                log.warn("Desktop alert was not delivered");
            }
        }
    }

    private synchronized PushService service() {
        if (pushService != null) {
            return pushService;
        }
        if (unavailable) {
            return null;
        }
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            PushService service = new PushService();
            service.setPublicKey(properties.getVapidPublicKey());
            service.setPrivateKey(properties.getVapidPrivateKey());
            service.setSubject(properties.getVapidSubject());
            pushService = service;
            return service;
        } catch (Exception ex) {
            unavailable = true;
            log.warn("Desktop alerts are not configured");
            return null;
        }
    }

    private static String payload(String title, String body, String url, String notificationId) {
        return "{\"title\":" + json(title)
                + ",\"body\":" + json(body)
                + ",\"url\":" + json(url == null ? "/notifications" : url)
                + ",\"id\":" + json(notificationId) + "}";
    }

    private static String json(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n")
                + "\"";
    }

    static String pathFor(String relatedType, UUID relatedId) {
        if (relatedId == null) {
            return "/notifications";
        }
        if ("REPAIR_REQUEST".equals(relatedType)) {
            return "/requests/" + relatedId;
        }
        if ("JOB".equals(relatedType) || "APPOINTMENT".equals(relatedType)) {
            return "/jobs/" + relatedId;
        }
        return "/notifications";
    }
}
