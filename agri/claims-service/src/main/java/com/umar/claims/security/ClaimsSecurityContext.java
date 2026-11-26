package com.umar.claims.security;

import com.umar.claims.exception.ClaimsApiException;
import org.springframework.http.HttpStatus;

public final class ClaimsSecurityContext {
    private static final ThreadLocal<ClaimsIdentity> CTX = new ThreadLocal<>();

    private ClaimsSecurityContext() {}

    public static void set(ClaimsIdentity id) { CTX.set(id); }
    public static ClaimsIdentity get() { return CTX.get(); }
    public static ClaimsIdentity require() {
        ClaimsIdentity id = CTX.get();
        if (id == null) throw new ClaimsApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
        return id;
    }
    public static void clear() { CTX.remove(); }
}
