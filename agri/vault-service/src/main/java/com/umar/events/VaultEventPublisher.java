package com.umar.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Publishes vault domain events to all registered {@link VaultEventListener}
 * consumers. Listener failures are logged but never propagated, so event
 * propagation cannot break the triggering operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VaultEventPublisher {

    private final List<VaultEventListener> listeners;

    public void publish(VaultEvent event) {
        for (VaultEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("VaultEventListener {} failed for event {}",
                        listener.getClass().getName(), event.type(), e);
            }
        }
    }
}
