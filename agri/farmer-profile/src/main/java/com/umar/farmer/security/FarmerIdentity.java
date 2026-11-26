package com.umar.farmer.security;

import com.umar.enums.auth.AuthRole;

import java.util.Set;
import java.util.UUID;

public record FarmerIdentity(UUID userId, String actorId, Set<AuthRole> roles, String serviceIdentity) {
    public boolean hasRole(AuthRole role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasAnyRole(AuthRole... candidate) {
        if (roles == null) return false;
        for (AuthRole r : candidate) {
            if (roles.contains(r)) return true;
        }
        return false;
    }
}
