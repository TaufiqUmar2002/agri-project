package com.umar.farmer.security;

import com.umar.enums.auth.AuthRole;
import com.umar.farmer.config.FarmerProperties;
import com.umar.security.AgreeShieldSecurityContext;
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
import java.util.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class FarmerIdentityFilter extends OncePerRequestFilter {

    private final FarmerProperties properties;
    private final ObjectProvider<SecurityToggleService> securityToggleProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            SecurityToggleService toggle = securityToggleProvider.getIfAvailable();
            if (toggle != null && !toggle.isSecurityEnabled("farmer-profile")) {
                AgreeShieldSecurityContext.set(com.umar.security.AgreeShieldPrincipal.anonymous(true));
                FarmerSecurityContext.set(new FarmerIdentity(null, "db-security-bypass", EnumSet.allOf(AuthRole.class), "db-security-bypass"));
                response.setHeader("X-Security-Mode", "BYPASSED");
                filterChain.doFilter(request, response);
                return;
            }
            if (properties.getSecurity().isAllowHeaderIdentity()) {
                String actorId = request.getHeader("X-Actor-Id");
                String rolesHeader = request.getHeader("X-Auth-Roles");
                String serviceId = request.getHeader("X-Service-Identity");
                if (actorId != null && !actorId.isBlank()) {
                    UUID uid = null;
                    try { uid = UUID.fromString(actorId.trim()); } catch (IllegalArgumentException ignored) {}
                    FarmerSecurityContext.set(new FarmerIdentity(uid, actorId.trim(), parseRoles(rolesHeader), serviceId));
                } else if (serviceId != null && !serviceId.isBlank()) {
                    FarmerSecurityContext.set(new FarmerIdentity(null, serviceId.trim(), parseRoles(rolesHeader), serviceId.trim()));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            FarmerSecurityContext.clear();
        }
    }

    private Set<AuthRole> parseRoles(String header) {
        if (header == null || header.isBlank()) return EnumSet.of(AuthRole.FARMER);
        Set<AuthRole> set = EnumSet.noneOf(AuthRole.class);
        Arrays.stream(header.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(r -> {
            try { set.add(AuthRole.valueOf(r.toUpperCase())); } catch (IllegalArgumentException ignored) {}
        });
        return set;
    }
}
