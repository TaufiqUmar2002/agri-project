package com.umar.integration.security;

import com.umar.integration.domain.enums.HubRole;

import java.util.Set;

public record HubIdentity(
        String actorId,
        String partnerId,
        String tenantId,
        String serviceName,
        Set<HubRole> roles,
        boolean strongAuth
) {
    public boolean hasRole(HubRole role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasAnyRole(HubRole... candidates) {
        if (roles == null) return false;
        for (HubRole candidate : candidates) {
            if (roles.contains(candidate)) return true;
        }
        return false;
    }

    public boolean isInternalStaff() {
        return hasAnyRole(
                HubRole.INTEGRATION_MANAGER,
                HubRole.COMPLIANCE_OFFICER,
                HubRole.AUDITOR,
                HubRole.ADMIN,
                HubRole.PLATFORM_PRODUCT,
                HubRole.SERVICE
        );
    }
}
