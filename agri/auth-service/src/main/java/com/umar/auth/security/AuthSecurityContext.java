package com.umar.auth.security;

import java.util.Optional;

public final class AuthSecurityContext {

    private static final ThreadLocal<AuthIdentity> CURRENT = new ThreadLocal<>();

    private AuthSecurityContext() {}

    public static void set(AuthIdentity identity) {
        CURRENT.set(identity);
    }

    public static Optional<AuthIdentity> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static AuthIdentity require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated identity in context"));
    }

    public static void clear() {
        CURRENT.remove();
    }
}
