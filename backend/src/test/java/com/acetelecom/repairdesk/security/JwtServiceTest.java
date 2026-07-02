package com.acetelecom.repairdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acetelecom.repairdesk.config.AppProperties;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

/** TEST-001: JWT minting and validation. */
class JwtServiceTest {

    private static final String SECRET =
            "test-secret-must-be-at-least-32-bytes-long-here";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret(SECRET);
        jwtService = new JwtService(props);
    }

    @Test
    void mintToken_then_validateAndParseClaims_roundtrip() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.ADMIN);

        String token = jwtService.mintToken(user);
        assertThat(token).isNotBlank();

        Claims claims = jwtService.validateAndParseClaims(token);
        assertThat(jwtService.extractUserId(claims)).isEqualTo(user.getId());
        assertThat(jwtService.extractRole(claims)).isEqualTo("ADMIN");
    }

    @Test
    void validateAndParseClaims_withTamperedToken_throwsJwtException() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.STAFF);

        String token = jwtService.mintToken(user);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.validateAndParseClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void mintToken_roleIsPreservedForAllRoles() {
        for (Role role : Role.values()) {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setRole(role);

            String token = jwtService.mintToken(user);
            Claims claims = jwtService.validateAndParseClaims(token);
            assertThat(jwtService.extractRole(claims)).isEqualTo(role.name());
        }
    }
}
