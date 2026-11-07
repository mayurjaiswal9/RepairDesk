package com.acetelecom.repairdesk.feature.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.push")
public class PushProperties {

    /** File path of a Firebase service-account JSON. */
    private String firebaseCredentials = "";

    /** Raw Firebase service-account JSON, for hosts that cannot mount a file. */
    private String firebaseCredentialsJson = "";

    private String vapidPublicKey = "";
    private String vapidPrivateKey = "";
    private String vapidSubject = "mailto:notifications@repairdesk.local";

    public boolean phoneConfigured() {
        return !blank(firebaseCredentials) || !blank(firebaseCredentialsJson);
    }

    public boolean desktopConfigured() {
        return !blank(vapidPublicKey) && !blank(vapidPrivateKey);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public String getFirebaseCredentials() {
        return firebaseCredentials;
    }

    public void setFirebaseCredentials(String firebaseCredentials) {
        this.firebaseCredentials = firebaseCredentials;
    }

    public String getFirebaseCredentialsJson() {
        return firebaseCredentialsJson;
    }

    public void setFirebaseCredentialsJson(String firebaseCredentialsJson) {
        this.firebaseCredentialsJson = firebaseCredentialsJson;
    }

    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    public void setVapidPublicKey(String vapidPublicKey) {
        this.vapidPublicKey = vapidPublicKey;
    }

    public String getVapidPrivateKey() {
        return vapidPrivateKey;
    }

    public void setVapidPrivateKey(String vapidPrivateKey) {
        this.vapidPrivateKey = vapidPrivateKey;
    }

    public String getVapidSubject() {
        return vapidSubject;
    }

    public void setVapidSubject(String vapidSubject) {
        this.vapidSubject = vapidSubject;
    }
}
