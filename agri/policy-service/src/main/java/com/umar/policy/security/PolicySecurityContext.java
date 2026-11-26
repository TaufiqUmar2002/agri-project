package com.umar.policy.security;

import com.umar.policy.exception.PolicyApiException;
import org.springframework.http.HttpStatus;

public final class PolicySecurityContext {
    private static final ThreadLocal<PolicyIdentity> CURRENT = new ThreadLocal<>();

    private PolicySecurityContext() {}

    public static void set(PolicyIdentity identity) { CURRENT.set(identity); }
    public static void clear() { CURRENT.remove(); }

    public static PolicyIdentity require() {
        PolicyIdentity id = CURRENT.get();
        if (id == null) {
            throw new PolicyApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
        }
        return id;
    }
}
