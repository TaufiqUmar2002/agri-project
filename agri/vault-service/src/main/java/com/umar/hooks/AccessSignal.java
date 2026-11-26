package com.umar.hooks;

import java.time.Instant;

/**
 * Access signal emitted after a protected vault operation completes, consumed by
 * {@code vault.post-access} hooks to forward to external security tools.
 */
public record AccessSignal(
        String caller,
        String operation,
        String dataClass,
        String purpose,
        String outcome,
        int recordCount,
        Instant timestamp
) {
    public static AccessSignal of(String caller, String operation, String dataClass,
                                  String purpose, String outcome, int recordCount) {
        return new AccessSignal(caller, operation, dataClass, purpose, outcome, recordCount, Instant.now());
    }
}
