package com.umar.auth.security;

import com.umar.auth.config.AuthProperties;
import com.umar.enums.auth.AuthRole;
import com.umar.security.AgreeShieldPrincipal;
import com.umar.security.AgreeShieldSecurityContext;
import com.umar.security.CentralSecurityFilter;
import com.umar.security.SecurityToggleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AuthIdentityFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AuthProperties properties;
    private final ObjectProvider<SecurityToggleService> securityToggleProvider;
    private final ObjectProvider<CentralSecurityFilter> centralSecurityProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            SecurityToggleService toggle = securityToggleProvider.getIfAvailable();
            if (toggle != null && !toggle.isSecurityEnabled("auth-service")) {
                CentralSecurityFilter central = centralSecurityProvider.getIfAvailable();
                AgreeShieldPrincipal p = central != null ? central.parsePrincipal(request, true) : AgreeShieldPrincipal.anonymous(true);
                AgreeShieldSecurityContext.set(p);
                AuthSecurityContext.set(new AuthIdentity(
                        p.userUuid(), p.userId(), EnumSet.allOf(AuthRole.class),
                        p.serviceId() != null ? p.serviceId() : "db-security-bypass",
                        p.sessionId(), "TIER_3", "en", extractClientIp(request)));
                response.setHeader("X-Security-Mode", "BYPASSED");
                filterChain.doFilter(request, response);
                return;
            }
            String clientIp = extractClientIp(request);
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                jwtTokenService.verifyToken(token).ifPresent(claims -> AuthSecurityContext.set(
                        new AuthIdentity(
                                UUID.fromString(claims.sub()),
                                claims.sub(),
                                claims.roles(),
                                request.getHeader("X-Service-Identity"),
                                claims.sid(),
                                claims.kycTier(),
                                claims.lang(),
                                clientIp
                        )
                ));
            } else if (properties.getSecurity().isAllowHeaderIdentity()) {
                String actorId = request.getHeader("X-Actor-Id");
                String serviceId = request.getHeader("X-Service-Identity");
                String rolesHeader = request.getHeader("X-Auth-Roles");
                if (actorId != null && !actorId.isBlank()) {
                    UUID uid = tryParseUuid(actorId);
                    Set<AuthRole> roles = parseRoles(rolesHeader);
                    AuthSecurityContext.set(new AuthIdentity(
                            uid, actorId, roles, serviceId, null, "TIER_0", "en", clientIp
                    ));
                } else if (serviceId != null && !serviceId.isBlank()) {
                    AuthSecurityContext.set(new AuthIdentity(
                            null, serviceId, parseRoles(rolesHeader), serviceId, null, null, "en", clientIp
                    ));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthSecurityContext.clear();
        }
    }

    private UUID tryParseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Set<AuthRole> parseRoles(String header) {
        if (header == null || header.isBlank()) {
            return EnumSet.of(AuthRole.FARMER);
        }
        Set<AuthRole> set = EnumSet.noneOf(AuthRole.class);
        Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(r -> {
                    try {
                        set.add(AuthRole.valueOf(r.toUpperCase()));
                    } catch (IllegalArgumentException ignored) {}
                });
        return set;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
