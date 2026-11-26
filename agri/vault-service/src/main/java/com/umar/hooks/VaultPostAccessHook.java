package com.umar.hooks;

/**
 * Extension point {@code vault.post-access} (section 2.16).
 *
 * Implementations receive an {@link AccessSignal} after a protected vault
 * operation completes, and forward it to external security tools (SIEM, DLP,
 * etc.). Hooks must be non-blocking and must never alter the operation result.
 */
public interface VaultPostAccessHook {

    /**
     * Handle an access signal emitted after a vault operation.
     *
     * @param signal the access signal describing the completed operation
     */
    void onAccess(AccessSignal signal);
}
