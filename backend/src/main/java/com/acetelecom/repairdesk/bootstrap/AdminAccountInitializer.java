package com.acetelecom.repairdesk.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.acetelecom.repairdesk.config.AppProperties;

/**
 * Seeds the default administrator account once (GAP-03 / DB-008).
 *
 * <p>Implemented in application code rather than a SQL migration so the password
 * is hashed with the same {@link PasswordEncoder} (BCrypt) used at login. The
 * insert is idempotent: it runs only when no {@code admin} account exists, so it
 * is safe across restarts.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public AdminAccountInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
            AppProperties appProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = appProperties.getAdmin();
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE lower(username) = lower(?) AND status <> 'DELETED'",
                Integer.class, admin.getDefaultUsername());

        if (existing != null && existing > 0) {
            log.info("Default admin account already present; skipping seed.");
            return;
        }

        String passwordHash = passwordEncoder.encode(admin.getDefaultPassword());
        jdbcTemplate.update(
                "INSERT INTO users (username, mobile, name, password_hash, role, status, must_change_password) "
                        + "VALUES (?, ?, ?, ?, 'ADMIN', 'ACTIVE', false)",
                admin.getDefaultUsername(), admin.getDefaultMobile(), "Administrator", passwordHash);

        log.warn("Seeded default admin '{}'.", admin.getDefaultUsername());
    }
}
