package com.umar.payout.security;

import com.umar.enums.auth.AuthRole;

import java.util.Set;

public record PayoutIdentity(String actorId, Set<AuthRole> roles, String serviceIdentity) {
    public boolean hasRole(AuthRole role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasAnyRole(AuthRole... allowed) {
        if (roles == null) return false;
        for (AuthRole r : allowed) {
            if (roles.contains(r)) return true;
        }
        return false;
    }

    public boolean isServiceCaller() {
        return serviceIdentity != null && !serviceIdentity.isBlank();
    }
}
