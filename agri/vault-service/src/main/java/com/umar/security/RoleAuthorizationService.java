package com.umar.security;

import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.exception.PolicyViolationException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the current caller's {@link VaultRole}s from the
 * {@link WorkloadIdentity} and enforces role-based authorization (section 2.11).
 */
@Service
public class RoleAuthorizationService {

    /** Returns the set of vault roles held by the current caller. */
    public Set<VaultRole> getCurrentRoles() {
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        if (identity == null || identity.roles() == null) {
            return EnumSet.noneOf(VaultRole.class);
        }
        return identity.roles().stream()
                .map(this::parseRole)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(VaultRole.class)));
    }

    /** Returns the current caller's service/identity name. */
    public String getCaller() {
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        return identity != null ? identity.serviceName() : "UNKNOWN";
    }

    public boolean hasAnyRole(VaultRole... roles) {
        Set<VaultRole> current = getCurrentRoles();
        return Arrays.stream(roles).anyMatch(current::contains);
    }

    /** Throws {@link PolicyViolationException} if the caller lacks any required role. */
    public void requireAnyRole(VaultRole... roles) {
        if (!hasAnyRole(roles)) {
            throw new PolicyViolationException(String.format(
                    "Access Denied: caller '%s' lacks required role(s) %s. Held roles: %s",
                    getCaller(), Arrays.toString(roles), getCurrentRoles()));
        }
    }

    /** Throws {@link PolicyViolationException} if the caller holds any of the denied roles. */
    public void denyAnyRole(VaultRole... roles) {
        Set<VaultRole> current = getCurrentRoles();
        for (VaultRole role : roles) {
            if (current.contains(role)) {
                throw new PolicyViolationException(String.format(
                        "Access Denied: caller '%s' with role '%s' is not permitted for this operation. Held roles: %s",
                        getCaller(), role, current));
            }
        }
    }

    private VaultRole parseRole(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return VaultRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
