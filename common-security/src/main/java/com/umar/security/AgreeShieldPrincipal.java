package com.umar.security;

import java.util.Set;
import java.util.UUID;

public record AgreeShieldPrincipal(
        UUID userUuid,
        String userId,
        Set<String> roles,
        String serviceId,
        String delegation,
        String stepUpLevel,
        String regulatorScope,
        String sessionId,
        String kycTier,
        String lang,
        String clientIp,
        boolean securityBypassed
) {
    public static AgreeShieldPrincipal anonymous(boolean bypassed) {
        return new AgreeShieldPrincipal(
                null, "anonymous", Set.of(), null, null, null, null, null, "TIER_0", "en", "127.0.0.1", bypassed);
    }

    public boolean hasRole(String role) {
        if (securityBypassed) {
            return true;
        }
        return roles != null && roles.contains(role);
    }

    public boolean hasAnyRole(String... allowed) {
        if (securityBypassed) {
            return true;
        }
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (String role : allowed) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean isStaff() {
        return hasAnyRole("ADMIN", "SUPPORT", "AUDITOR", "COMPLIANCE_OFFICER",
                "FINANCE", "OPS", "INTEGRATION_MANAGER", "TRIGGER_REVIEWER",
                "CLAIMS_REVIEWER", "SECURITY_OFFICER");
    }
}
