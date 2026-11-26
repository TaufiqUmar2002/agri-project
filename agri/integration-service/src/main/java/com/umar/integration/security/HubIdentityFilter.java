package com.umar.integration.security;

import com.umar.integration.config.HubProperties;
import com.umar.integration.domain.enums.HubRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Resolves caller identity from mTLS certificates, JWT Bearer tokens, or configured dev headers
 * into {@link HubSecurityContext}. Public inbound connector routes (/api/integrations/inbound/*)
 * handle connector-specific authentication inside INT-09.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class HubIdentityFilter extends OncePerRequestFilter {

    private final HubProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/") || path.startsWith("/api/integrations/inbound/")) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                HubSecurityContext.clear();
            }
            return;
        }

        try {
            HubIdentity resolved = resolveFromHeadersOrJwt(request);
            if (resolved == null && properties.getAuth().isDevFallbackEnabled()) {
                resolved = new HubIdentity(
                        "dev-admin",
                        null,
                        null,
                        "local-dev",
                        Set.of(HubRole.ADMIN, HubRole.INTEGRATION_MANAGER),
                        true
                );
            }

            if (resolved == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"error\":\"Authentication required\"}");
                return;
            }

            HubSecurityContext.set(resolved);
            filterChain.doFilter(request, response);
        } finally {
            HubSecurityContext.clear();
        }
    }

    private HubIdentity resolveFromHeadersOrJwt(HttpServletRequest request) {
        if (properties.getAuth().isAllowHeaderIdentity()) {
            String actor = firstNonBlank(request.getHeader("X-Actor-Id"), request.getHeader("X-Service-Identity"));
            String rolesHeader = request.getHeader("X-Hub-Roles");
            String partnerId = trimToNull(request.getHeader("X-Partner-Id"));
            String tenantId = trimToNull(request.getHeader("X-Tenant-Id"));
            if (actor != null || rolesHeader != null) {
                Set<HubRole> roles = parseRoles(rolesHeader);
                if (roles.isEmpty() && partnerId != null) {
                    roles = Set.of(HubRole.PARTNER_ADMIN);
                } else if (roles.isEmpty()) {
                    roles = Set.of(HubRole.SERVICE);
                }
                return new HubIdentity(
                        actor != null ? actor : (partnerId != null ? partnerId : "header-actor"),
                        partnerId,
                        tenantId,
                        trimToNull(request.getHeader("X-Service-Identity")),
                        roles,
                        true
                );
            }
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return parseJwtPayload(authHeader.substring(7).trim());
        }
        return null;
    }

    private HubIdentity parseJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
            String sub = claims.path("sub").asText("jwt-user");
            String partnerId = claims.hasNonNull("partner_id") ? claims.get("partner_id").asText() : null;
            String tenantId = claims.hasNonNull("tenant_id") ? claims.get("tenant_id").asText() : null;
            Set<HubRole> roles = new LinkedHashSet<>();
            if (claims.has("roles") && claims.get("roles").isArray()) {
                for (JsonNode r : claims.get("roles")) {
                    try {
                        roles.add(HubRole.valueOf(r.asText().trim().toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            if (roles.isEmpty() && partnerId != null) {
                roles.add(HubRole.PARTNER_ADMIN);
            }
            return new HubIdentity(sub, partnerId, tenantId, null, roles, true);
        } catch (Exception e) {
            return null;
        }
    }

    private Set<HubRole> parseRoles(String header) {
        Set<HubRole> result = new LinkedHashSet<>();
        if (header == null || header.isBlank()) {
            return result;
        }
        for (String part : header.split(",")) {
            String trimmed = part.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                try {
                    result.add(HubRole.valueOf(trimmed));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return result;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    private static String trimToNull(String val) {
        return (val == null || val.isBlank()) ? null : val.trim();
    }
}
