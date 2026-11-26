package com.umar.trigger.security;

import com.umar.trigger.exception.TriggerApiException;
import org.springframework.http.HttpStatus;

public final class TriggerSecurityContext {
    private static final ThreadLocal<TriggerIdentity> CTX = new ThreadLocal<>();

    private TriggerSecurityContext() {}

    public static void set(TriggerIdentity id) {
        CTX.set(id);
    }

    public static TriggerIdentity get() {
        return CTX.get();
    }

    public static TriggerIdentity require() {
        TriggerIdentity id = CTX.get();
        if (id == null) {
            throw new TriggerApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
        }
        return id;
    }

    public static void clear() {
        CTX.remove();
    }
}
