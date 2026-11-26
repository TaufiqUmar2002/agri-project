package com.umar.audit.security;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuditIdentityFilter extends OncePerRequestFilter {

    private final ObjectProvider<SecurityToggleService> securityToggleProvider;
    private static final ThreadLocal<Identity> CONTEXT = new ThreadLocal<>();

    public record Identity(
            String userId,
            Set<String> roles,
            String regulatorScope
    ) {
        public boolean hasRole(String role) {
            if (AgreeShieldSecurityContext.current().securityBypassed()) return true;
            return roles.contains(role);
        }

        public boolean hasAnyRole(String... allowed) {
            if (AgreeShieldSecurityContext.current().securityBypassed()) return true;
            for (String r : allowed) {
                if (roles.contains(r)) return true;
            }
            return false;
        }
    }

    public static Identity current() {
        Identity id = CONTEXT.get();
        return id != null ? id : new Identity("anonymous", Set.of(), null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            SecurityToggleService toggle = securityToggleProvider.getIfAvailable();
            if (toggle != null && !toggle.isSecurityEnabled("audit-service")) {
                AgreeShieldSecurityContext.set(com.umar.security.AgreeShieldPrincipal.anonymous(true));
                CONTEXT.set(new Identity("db-security-bypass",
                        Set.of("COMPLIANCE_OFFICER", "AUDITOR", "SECURITY_OFFICER"), null));
                res.setHeader("X-Security-Mode", "BYPASSED");
                chain.doFilter(req, res);
                return;
            }
            String userId = req.getHeader("X-User-Id");
            String rolesHeader = req.getHeader("X-User-Roles");
            if (rolesHeader == null || rolesHeader.isBlank()) {
                rolesHeader = req.getHeader("X-User-Role");
            }
            Set<String> roles = rolesHeader == null || rolesHeader.isBlank()
                    ? Collections.emptySet()
                    : Arrays.stream(rolesHeader.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());
            CONTEXT.set(new Identity(
                    userId == null || userId.isBlank() ? "anonymous" : userId.trim(),
                    roles,
                    req.getHeader("X-Regulator-Scope")
            ));
            chain.doFilter(req, res);
        } finally {
            CONTEXT.remove();
        }
    }
}
