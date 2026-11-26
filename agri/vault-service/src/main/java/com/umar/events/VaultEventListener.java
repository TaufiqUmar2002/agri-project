package com.umar.events;

/**
 * Consumer of vault domain events. Implementations act as the "consumers" from
 * section 2.14 (security monitoring, SIEM, audit, auth JWKS refresh, etc.).
 */
public interface VaultEventListener {

    /**
     * Handle a vault domain event.
     *
     * @param event the event to consume
     */
    void onEvent(VaultEvent event);
}
