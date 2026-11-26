package com.umar.plot.security;

import com.umar.enums.auth.AuthRole;

import java.util.Set;
import java.util.UUID;

public record PlotIdentity(UUID userId, String actorId, Set<AuthRole> roles, String serviceIdentity) {
    public boolean hasRole(AuthRole role) { return roles != null && roles.contains(role); }
    public boolean hasAnyRole(AuthRole... cands) {
        if (roles == null) return false;
        for (AuthRole r : cands) if (roles.contains(r)) return true;
        return false;
    }
    public boolean isServiceCaller() { return serviceIdentity != null && !serviceIdentity.isBlank(); }
}
