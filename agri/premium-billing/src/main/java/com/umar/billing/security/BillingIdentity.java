package com.umar.billing.security;

import com.umar.enums.auth.AuthRole;

import java.util.Set;
import java.util.UUID;

public record BillingIdentity(
        String actorId,
        UUID userId,
        Set<AuthRole> roles,
        String sponsorId,
        boolean collector,
        String serviceIdentity
) {
    public boolean isServiceCaller() {
        return serviceIdentity != null && !serviceIdentity.isBlank();
    }

    public boolean hasRole(AuthRole role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasAnyRole(AuthRole... check) {
        if (roles == null) return false;
        for (AuthRole r : check) {
            if (roles.contains(r)) return true;
        }
        return false;
    }
}
