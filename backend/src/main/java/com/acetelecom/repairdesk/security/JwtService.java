package com.acetelecom.repairdesk.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.acetelecom.repairdesk.config.AppProperties;
import com.acetelecom.repairdesk.feature.user.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Mints and validates non-expiring JWTs (ADR-003).
 *
 * <p>Tokens carry only {@code sub} (userId) and {@code role} — no sensitive data.
 * Revocation is handled by the per-request account-status check in
 * {@link JwtAuthFilter}, not by expiry.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;

    public JwtService(AppProperties appProperties) {
        byte[] keyBytes = appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String mintToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates the token signature.
     *
     * @return parsed claims
     * @throws JwtException if the token is malformed, tampered, or uses the wrong key
     */
    public Claims validateAndParseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String extractRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }
}
