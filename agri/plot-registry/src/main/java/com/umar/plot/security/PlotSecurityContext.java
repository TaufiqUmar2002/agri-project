package com.umar.plot.security;

import java.util.Optional;

public final class PlotSecurityContext {
    private static final ThreadLocal<PlotIdentity> CURRENT = new ThreadLocal<>();
    private PlotSecurityContext() {}
    public static void set(PlotIdentity id) { CURRENT.set(id); }
    public static Optional<PlotIdentity> get() { return Optional.ofNullable(CURRENT.get()); }
    public static PlotIdentity require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated identity"));
    }
    public static void clear() { CURRENT.remove(); }
}
