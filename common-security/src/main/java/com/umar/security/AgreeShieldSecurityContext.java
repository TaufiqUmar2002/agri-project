package com.umar.security;

public final class AgreeShieldSecurityContext {

    private static final ThreadLocal<AgreeShieldPrincipal> CTX = new ThreadLocal<>();

    private AgreeShieldSecurityContext() {
    }

    public static void set(AgreeShieldPrincipal principal) {
        CTX.set(principal);
    }

    public static AgreeShieldPrincipal get() {
        return CTX.get();
    }

    public static AgreeShieldPrincipal current() {
        AgreeShieldPrincipal p = CTX.get();
        return p != null ? p : AgreeShieldPrincipal.anonymous(false);
    }

    public static void clear() {
        CTX.remove();
    }
}
