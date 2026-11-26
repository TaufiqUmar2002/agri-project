package com.umar.billing.security;

import com.umar.billing.exception.BillingApiException;
import org.springframework.http.HttpStatus;

public final class BillingSecurityContext {
    private static final ThreadLocal<BillingIdentity> CURRENT = new ThreadLocal<>();

    private BillingSecurityContext() {}

    public static void set(BillingIdentity identity) { CURRENT.set(identity); }
    public static void clear() { CURRENT.remove(); }

    public static BillingIdentity require() {
        BillingIdentity id = CURRENT.get();
        if (id == null) {
            throw new BillingApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
        }
        return id;
    }
}
