package com.acetelecom.repairdesk.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates the {@code Authorization: Bearer <token>} header on every request.
 *
 * <p>After signature verification the filter re-fetches the account and rejects
 * DISABLED or DELETED accounts even if the token is cryptographically valid
 * (gap-analysis JWT revocation rule).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        CompanyContext.clear();
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith(BEARER_PREFIX)) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.validateAndParseClaims(token);
                UUID userId = jwtService.extractUserId(claims);
                String role = jwtService.extractRole(claims);
                UUID companyId = jwtService.extractCompanyId(claims);

                User user = userRepository.findById(userId).orElse(null);
                if (user == null || !user.isActive() || companyId == null
                        || user.getCompanyId() == null || !companyId.equals(user.getCompanyId())) {
                    filterChain.doFilter(request, response);
                    return;
                }

                CompanyContext.set(user.getCompanyId());
                var auth = new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(user.getId(), user.getUsername(), role, user.getCompanyId()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid token — leave SecurityContext empty; security config returns 401.
            }

            filterChain.doFilter(request, response);
        } finally {
            CompanyContext.clear();
        }
    }
}
