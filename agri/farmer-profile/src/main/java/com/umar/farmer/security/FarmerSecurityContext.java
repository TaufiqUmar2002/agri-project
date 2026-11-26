package com.umar.farmer.security;

import java.util.Optional;

public final class FarmerSecurityContext {
    private static final ThreadLocal<FarmerIdentity> CURRENT = new ThreadLocal<>();

    private FarmerSecurityContext() {}

    public static void set(FarmerIdentity id) { CURRENT.set(id); }
    public static Optional<FarmerIdentity> get() { return Optional.ofNullable(CURRENT.get()); }
    public static FarmerIdentity require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated identity"));
    }
    public static void clear() { CURRENT.remove(); }
}
