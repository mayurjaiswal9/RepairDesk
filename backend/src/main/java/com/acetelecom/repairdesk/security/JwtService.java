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
 * <p>Tokens carry {@code sub} (userId), {@code role}, and {@code companyId}.
 * Revocation is handled by the per-request account-status check in
 * {@link JwtAuthFilter}, not by expiry.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_COMPANY = "companyId";

    private final SecretKey signingKey;

    public JwtService(AppProperties appProperties) {
        byte[] keyBytes = appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String mintToken(User user) {
        if (user.getCompanyId() == null) {
            throw new IllegalStateException("User has no company");
        }
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_COMPANY, user.getCompanyId().toString())
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

    /** Null when the token was issued before a company was stored on the token. */
    public UUID extractCompanyId(Claims claims) {
        String value = claims.get(CLAIM_COMPANY, String.class);
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}
