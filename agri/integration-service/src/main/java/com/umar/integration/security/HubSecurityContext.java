package com.umar.integration.security;

import com.umar.integration.domain.enums.HubRole;
import com.umar.integration.error.HubException;

import java.util.Set;

public final class HubSecurityContext {

    private static final ThreadLocal<HubIdentity> CURRENT = new ThreadLocal<>();

    private HubSecurityContext() {}

    public static void set(HubIdentity identity) {
        CURRENT.set(identity);
    }

    public static HubIdentity get() {
        return CURRENT.get();
    }

    public static HubIdentity require() {
        HubIdentity identity = CURRENT.get();
        if (identity == null) {
            throw HubException.unauthenticated("Authentication is required");
        }
        return identity;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static HubIdentity systemService(String serviceName) {
        return new HubIdentity(serviceName, null, null, serviceName, Set.of(HubRole.SERVICE), true);
    }
}
