package com.acetelecom.repairdesk.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Strongly-typed binding for the {@code app.*} configuration namespace.
 * Values are environment-overridable (see application.yml).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Cors cors = new Cors();
    private final Jwt jwt = new Jwt();
    private final Storage storage = new Storage();
    private final Security security = new Security();
    private final Admin admin = new Admin();

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of();
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
    }

    @Getter
    @Setter
    public static class Storage {
        private String location;
    }

    @Getter
    @Setter
    public static class Security {
        private int passwordMinLength = 8;
    }

    @Getter
    @Setter
    public static class Admin {
        private String defaultUsername;
        private String defaultPassword;
        private String defaultMobile;
    }
}
