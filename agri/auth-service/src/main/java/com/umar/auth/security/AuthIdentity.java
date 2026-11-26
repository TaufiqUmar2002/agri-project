package com.umar.auth.security;

import com.umar.enums.auth.AuthRole;

import java.util.Set;
import java.util.UUID;

public record AuthIdentity(
        UUID userId,
        String actorId,
        Set<AuthRole> roles,
        String serviceIdentity,
        String sessionId,
        String kycTier,
        String preferredLanguage,
        String clientIp
) {
    public boolean hasRole(AuthRole role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasAnyRole(AuthRole... candidateRoles) {
        if (roles == null) return false;
        for (AuthRole r : candidateRoles) {
            if (roles.contains(r)) return true;
        }
        return false;
    }
}
