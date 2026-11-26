package com.umar.iot.security;

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
public class IotIdentityFilter extends OncePerRequestFilter {
    private final ObjectProvider<SecurityToggleService> securityToggleProvider;
    public record Identity(String actorId, Set<AuthRole> roles, String serviceIdentity) {
        public boolean hasRole(AuthRole r) { return roles != null && roles.contains(r); }
        public boolean hasAnyRole(AuthRole... a) {
            if (roles == null) return false;
            for (AuthRole r : a) if (roles.contains(r)) return true;
            return false;
        }
    }

    public static final ThreadLocal<Identity> CTX = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            SecurityToggleService toggle = securityToggleProvider.getIfAvailable();
            if (toggle != null && !toggle.isSecurityEnabled("iot-service")) {
                AgreeShieldSecurityContext.set(com.umar.security.AgreeShieldPrincipal.anonymous(true));
                CTX.set(new Identity("db-security-bypass", EnumSet.allOf(AuthRole.class), "db-security-bypass"));
                res.setHeader("X-Security-Mode", "BYPASSED");
                chain.doFilter(req, res);
                return;
            }
            String actorId = req.getHeader("X-Actor-Id");
            String rolesHdr = req.getHeader("X-Auth-Roles");
            String svcId = req.getHeader("X-Service-Identity");
            if ((actorId != null && !actorId.isBlank()) || (svcId != null && !svcId.isBlank())) {
                Set<AuthRole> roles = EnumSet.noneOf(AuthRole.class);
                if (rolesHdr != null && !rolesHdr.isBlank()) {
                    for (String r : rolesHdr.split(",")) {
                        try { roles.add(AuthRole.valueOf(r.trim().toUpperCase())); } catch (IllegalArgumentException ignored) {}
                    }
                }
                CTX.set(new Identity(actorId != null && !actorId.isBlank() ? actorId : svcId, roles, svcId));
            }
            chain.doFilter(req, res);
        } finally {
            CTX.remove();
        }
    }
}
