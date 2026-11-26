package com.umar.authentication;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves mTLS X.509 SPIFFE service identities (or configured dev/bastion headers)
 * into {@link VaultSecurityContext} and clears it after each request.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class VaultIdentityFilter extends OncePerRequestFilter {

    private static final String CERT_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    private final CertificateIdentityResolver certificateIdentityResolver;

    @Value("${vault.auth.require-mtls:false}")
    private boolean requireMtls;

    @Value("${vault.auth.allow-header-identity:true}")
    private boolean allowHeaderIdentity;

    @Value("${vault.auth.dev-fallback-enabled:true}")
    private boolean devFallbackEnabled;

    @PostConstruct
    void syncContextConfig() {
        VaultSecurityContext.setDevFallbackEnabled(devFallbackEnabled && !requireMtls);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        // Public actuator health and single-use browser viewer links handle their own session/link validation
        if (path.startsWith("/actuator/health") || path.startsWith("/api/vault/viewer/")) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                VaultSecurityContext.clear();
            }
            return;
        }

        try {
            Object certificates = request.getAttribute(CERT_ATTRIBUTE);
            if (certificates instanceof X509Certificate[] certs && certs.length > 0) {
                try {
                    WorkloadIdentity identity = certificateIdentityResolver.toWorkloadIdentity(certs[0]);
                    String rolesHeader = request.getHeader("X-Vault-Roles");
                    if (rolesHeader != null && !rolesHeader.isBlank()) {
                        Set<String> roles = parseRoles(rolesHeader);
                        identity = new WorkloadIdentity(identity.serviceName(), identity.environment(), roles);
                    }
                    VaultSecurityContext.setWorkloadIdentity(identity);
                    VaultSecurityContext.setStrongAuth(isStrongAuthHeader(request));
                    filterChain.doFilter(request, response);
                    return;
                } catch (IllegalArgumentException ex) {
                    writeUnauthorized(response, "IDENTITY_NOT_ACCEPTED", "No valid service identity on the connection");
                    return;
                }
            }

            if (requireMtls) {
                writeUnauthorized(response, "IDENTITY_NOT_ACCEPTED", "No valid service identity on the connection");
                return;
            }

            if (allowHeaderIdentity) {
                String caller = firstNonBlank(
                        request.getHeader("X-Service-Identity"),
                        request.getHeader("X-Vault-Caller"));
                if (caller != null) {
                    String normalizedCaller = normalizeCaller(caller);
                    String rolesHeader = request.getHeader("X-Vault-Roles");
                    Set<String> roles = (rolesHeader != null && !rolesHeader.isBlank())
                            ? parseRoles(rolesHeader)
                            : Set.of("SERVICE");
                    VaultSecurityContext.setWorkloadIdentity(new WorkloadIdentity(normalizedCaller, "local", roles));
                    VaultSecurityContext.setStrongAuth(isStrongAuthHeader(request));
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            if (devFallbackEnabled) {
                VaultSecurityContext.setWorkloadIdentity(
                        new WorkloadIdentity("TEMP-SERVICE", "local", Set.of("SERVICE", "MAKER", "VIEWER")));
                VaultSecurityContext.setStrongAuth(true);
                filterChain.doFilter(request, response);
                return;
            }

            writeUnauthorized(response, "IDENTITY_NOT_ACCEPTED", "No valid service identity on the connection");
        } finally {
            VaultSecurityContext.clear();
        }
    }

    private boolean isStrongAuthHeader(HttpServletRequest request) {
        String strong = request.getHeader("X-Strong-Auth");
        return strong == null || "true".equalsIgnoreCase(strong) || "1".equals(strong);
    }

    private String normalizeCaller(String caller) {
        String trimmed = caller.trim();
        if (trimmed.startsWith("spiffe://")) {
            String[] parts = trimmed.split("/");
            return parts[parts.length - 1].toUpperCase();
        }
        return trimmed.toUpperCase();
    }

    private Set<String> parseRoles(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase().replace('-', '_').replace(' ', '_'))
                .collect(Collectors.toSet());
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"error\":\"" + message + "\"}");
    }
}
