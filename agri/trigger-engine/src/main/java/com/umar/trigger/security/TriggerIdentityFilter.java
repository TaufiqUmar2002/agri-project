package com.umar.trigger.security;

import com.umar.enums.auth.AuthRole;
import com.umar.security.AgreeShieldSecurityContext;
import com.umar.security.SecurityToggleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TriggerIdentityFilter extends OncePerRequestFilter {
    private final ObjectProvider<SecurityToggleService> securityToggleProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            SecurityToggleService toggle = securityToggleProvider.getIfAvailable();
            if (toggle != null && !toggle.isSecurityEnabled("trigger-engine")) {
                AgreeShieldSecurityContext.set(com.umar.security.AgreeShieldPrincipal.anonymous(true));
                TriggerSecurityContext.set(new TriggerIdentity("db-security-bypass", EnumSet.allOf(AuthRole.class), "db-security-bypass"));
                response.setHeader("X-Security-Mode", "BYPASSED");
                filterChain.doFilter(request, response);
                return;
            }
            String actorId = request.getHeader("X-Actor-Id");
            String rolesHeader = request.getHeader("X-Auth-Roles");
            String serviceId = request.getHeader("X-Service-Identity");
            if ((actorId != null && !actorId.isBlank()) || (serviceId != null && !serviceId.isBlank())) {
                Set<AuthRole> roles = EnumSet.noneOf(AuthRole.class);
                if (rolesHeader != null && !rolesHeader.isBlank()) {
                    for (String r : rolesHeader.split(",")) {
                        try {
                            roles.add(AuthRole.valueOf(r.trim().toUpperCase()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                TriggerSecurityContext.set(new TriggerIdentity(
                        actorId != null && !actorId.isBlank() ? actorId : serviceId,
                        roles, serviceId));
            }
            filterChain.doFilter(request, response);
        } finally {
            TriggerSecurityContext.clear();
        }
    }
}
