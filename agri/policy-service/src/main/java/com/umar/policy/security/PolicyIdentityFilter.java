package com.umar.policy.security;

import com.umar.enums.auth.AuthRole;
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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class PolicyIdentityFilter extends OncePerRequestFilter {

    private final ObjectProvider<SecurityToggleService> securityToggleProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            SecurityToggleService toggle = securityToggleProvider.getIfAvailable();
            if (toggle != null && !toggle.isSecurityEnabled("policy-service")) {
                AgreeShieldSecurityContext.set(com.umar.security.AgreeShieldPrincipal.anonymous(true));
                PolicySecurityContext.set(new PolicyIdentity("db-security-bypass", null, EnumSet.allOf(AuthRole.class), "db-security-bypass"));
                res.setHeader("X-Security-Mode", "BYPASSED");
                chain.doFilter(req, res);
                return;
            }
            String actorHeader = req.getHeader("X-Actor-Id");
            String rolesHeader = req.getHeader("X-Auth-Roles");
            String serviceHeader = req.getHeader("X-Service-Identity");

            if ((actorHeader != null && !actorHeader.isBlank()) || (serviceHeader != null && !serviceHeader.isBlank())) {
                UUID userId = null;
                if (actorHeader != null && !actorHeader.isBlank()) {
                    try { userId = UUID.fromString(actorHeader.trim()); } catch (IllegalArgumentException ignored) {}
                }
                Set<AuthRole> roles = EnumSet.noneOf(AuthRole.class);
                if (rolesHeader != null && !rolesHeader.isBlank()) {
                    Arrays.stream(rolesHeader.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .forEach(r -> {
                                try { roles.add(AuthRole.valueOf(r)); } catch (IllegalArgumentException ignored) {}
                            });
                }
                String actorId = (actorHeader != null && !actorHeader.isBlank()) ? actorHeader.trim() : serviceHeader.trim();
                PolicySecurityContext.set(new PolicyIdentity(actorId, userId, roles, serviceHeader));
            }
            chain.doFilter(req, res);
        } finally {
            PolicySecurityContext.clear();
        }
    }
}
