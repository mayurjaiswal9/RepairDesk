package com.acetelecom.repairdesk.feature.notification.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.acetelecom.repairdesk.feature.notification.config.PushProperties;
import com.acetelecom.repairdesk.feature.notification.domain.DeviceToken;
import com.acetelecom.repairdesk.feature.notification.repository.DeviceTokenRepository;
import com.google.auth.oauth2.GoogleCredentials;

@Service
public class PhonePushSender {

    private static final Logger log = LoggerFactory.getLogger(PhonePushSender.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private static final Pattern PROJECT_ID = Pattern.compile("\"project_id\"\\s*:\\s*\"([^\"]+)\"");

    private final DeviceTokenRepository tokens;
    private final PushProperties properties;
    private final RestClient http = RestClient.create();

    private GoogleCredentials credentials;
    private String projectId;
    private boolean unavailable;

    public PhonePushSender(DeviceTokenRepository tokens, PushProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    @Transactional
    public void send(UUID userId, String title, String body, Map<String, String> data) {
        if (userId == null || !properties.phoneConfigured()) {
            return;
        }
        if (!ready()) {
            return;
        }
        for (DeviceToken device : List.copyOf(tokens.findByUserId(userId))) {
            try {
                post(device.getToken(), title, body, data);
            } catch (RestClientResponseException ex) {
                String response = ex.getResponseBodyAsString();
                if (ex.getStatusCode().value() == 404 || response.contains("UNREGISTERED")) {
                    tokens.delete(device);
                } else {
                    log.warn("Phone alert was not delivered");
                }
            } catch (Exception ex) {
                log.warn("Phone alert was not delivered");
            }
        }
    }

    private void post(String token, String title, String body, Map<String, String> data) throws Exception {
        credentials.refreshIfExpired();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("token", token);
        message.put("notification", Map.of("title", title, "body", body));
        message.put("data", data == null ? Map.of() : data);
        message.put("android", Map.of(
                "priority", "HIGH",
                "notification", Map.of("channel_id", "repairdesk_alerts")));
        http.post()
                .uri("https://fcm.googleapis.com/v1/projects/{project}/messages:send", projectId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.getAccessToken().getTokenValue())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", message))
                .retrieve()
                .toBodilessEntity();
    }

    private synchronized boolean ready() {
        if (credentials != null) {
            return true;
        }
        if (unavailable) {
            return false;
        }
        try {
            String json = new String(loadJson(), StandardCharsets.UTF_8);
            Matcher matcher = PROJECT_ID.matcher(json);
            if (!matcher.find()) {
                unavailable = true;
                return false;
            }
            projectId = matcher.group(1);
            credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(List.of(SCOPE));
            return true;
        } catch (Exception ex) {
            unavailable = true;
            log.warn("Phone alerts are not configured");
            return false;
        }
    }

    private byte[] loadJson() throws Exception {
        String raw = properties.getFirebaseCredentialsJson();
        if (raw != null && !raw.isBlank()) {
            return raw.getBytes(StandardCharsets.UTF_8);
        }
        return Files.readAllBytes(Path.of(properties.getFirebaseCredentials()));
    }
}
