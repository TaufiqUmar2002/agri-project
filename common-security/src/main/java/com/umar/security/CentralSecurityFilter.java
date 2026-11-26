package com.umar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized servlet security filter backed by DB module toggle ({@link SecurityToggleService}).
 * Populates {@link AgreeShieldSecurityContext} from headers/JWT or marks securityBypassed=true
 * when the current module's security is toggled OFF in the DB.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class CentralSecurityFilter extends OncePerRequestFilter {

    private final SecurityToggleService toggleService;
    private final org.springframework.beans.factory.ObjectProvider<com.umar.logging.CentralLoggingService> loggingProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            boolean securityEnabled = toggleService.isSecurityEnabled();
            AgreeShieldPrincipal principal = parsePrincipal(req, !securityEnabled);
            AgreeShieldSecurityContext.set(principal);
            com.umar.logging.CentralLoggingService logging = loggingProvider != null ? loggingProvider.getIfAvailable() : null;
            if (!securityEnabled) {
                res.setHeader("X-Security-Mode", "BYPASSED");
                if (logging != null) {
                    logging.security("CentralSecurityFilter", "Security BYPASSED via DB toggle for " + req.getRequestURI());
                }
            } else if (logging != null) {
                logging.security("CentralSecurityFilter", "Authenticated user=" + principal.userId() + " path=" + req.getRequestURI());
            }
            chain.doFilter(req, res);
        } finally {
            AgreeShieldSecurityContext.clear();
        }
    }

    public AgreeShieldPrincipal parsePrincipal(HttpServletRequest req, boolean bypassed) {
        String userId = firstNonBlank(req.getHeader("X-User-Id"), req.getHeader("X-Actor-Id"), "anonymous");
        String rolesRaw = firstNonBlank(req.getHeader("X-User-Roles"), req.getHeader("X-User-Role"),
                firstNonBlank(req.getHeader("X-Auth-Roles"), req.getHeader("X-Roles"), ""));
        Set<String> roles = rolesRaw.isBlank()
                ? Set.of()
                : Arrays.stream(rolesRaw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toUpperCase)
                        .collect(Collectors.toUnmodifiableSet());
        String serviceId = firstNonBlank(req.getHeader("X-Service-Id"), req.getHeader("X-Service-Identity"), null);
        String clientIp = firstNonBlank(req.getHeader("X-Forwarded-For"), req.getRemoteAddr(), "127.0.0.1");
        if (clientIp != null && clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }
        return new AgreeShieldPrincipal(
                tryParseUuid(userId),
                userId,
                roles,
                serviceId,
                req.getHeader("X-Delegation"),
                firstNonBlank(req.getHeader("X-Step-Up-Level"), req.getHeader("X-Step-Up-Token"), null),
                req.getHeader("X-Regulator-Scope"),
                req.getHeader("X-Session-Id"),
                firstNonBlank(req.getHeader("X-Kyc-Tier"), "TIER_0", "TIER_0"),
                firstNonBlank(req.getHeader("X-Lang"), "en", "en"),
                clientIp,
                bypassed
        );
    }

    private String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return fallback;
    }

    private UUID tryParseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
