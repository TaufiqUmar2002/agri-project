package com.umar.service;

import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.events.VaultEvent;
import com.umar.events.VaultEventPublisher;
import com.umar.events.VaultEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central emitter for vault domain events (section 2.14). Enriches payloads
 * with the caller identity and publishes via {@link VaultEventPublisher}.
 * Also performs simple access-volume anomaly detection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VaultEventEmitter {

    private final VaultEventPublisher publisher;

    // Simple sliding-window anomaly tracker: key -> (windowStartEpochSec, count)
    private final ConcurrentHashMap<String, Window> accessWindows = new ConcurrentHashMap<>();
    private static final long ANOMALY_WINDOW_SECONDS = 60;
    private static final int ANOMALY_THRESHOLD = 200;

    public void emit(VaultEventType type, Map<String, Object> payload) {
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        String caller = identity != null ? identity.serviceName() : "UNKNOWN";

        Map<String, Object> enriched = new HashMap<>(payload == null ? Map.of() : payload);
        enriched.put("caller", caller);
        enriched.putIfAbsent("timestamp", java.time.Instant.now().toString());

        publisher.publish(VaultEvent.of(type, enriched, null));
    }

    /**
     * Emits {@code vault.access.logged} for an access operation and checks for
     * unusual volume, emitting {@code vault.access.anomaly} when the threshold
     * is exceeded within the window.
     */
    public void emitAccess(String operation, String dataClass, String purpose, String outcome, int recordCount) {
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        String caller = identity != null ? identity.serviceName() : "UNKNOWN";

        Map<String, Object> payload = new HashMap<>();
        payload.put("caller", caller);
        payload.put("operation", operation);
        payload.put("dataClass", dataClass);
        payload.put("purpose", purpose);
        payload.put("outcome", outcome);
        payload.put("recordCount", recordCount);

        publisher.publish(VaultEvent.of(VaultEventType.ACCESS_LOGGED, payload, null));

        // Anomaly detection on access volume per caller+dataClass.
        String key = caller + "|" + (dataClass == null ? "*" : dataClass);
        long nowSec = System.currentTimeMillis() / 1000;
        Window window = accessWindows.compute(key, (k, existing) -> {
            if (existing == null || nowSec - existing.startSec >= ANOMALY_WINDOW_SECONDS) {
                return new Window(nowSec, new AtomicInteger(recordCount));
            }
            existing.count.addAndGet(recordCount);
            return existing;
        });

        if (window.count.get() > ANOMALY_THRESHOLD) {
            Map<String, Object> anomaly = new HashMap<>(payload);
            anomaly.put("windowSeconds", ANOMALY_WINDOW_SECONDS);
            anomaly.put("observedCount", window.count.get());
            anomaly.put("threshold", ANOMALY_THRESHOLD);
            publisher.publish(VaultEvent.of(VaultEventType.ACCESS_ANOMALY, anomaly, null));
            log.warn("Access anomaly detected for {}: {} accesses in {}s", key, window.count.get(), ANOMALY_WINDOW_SECONDS);
        }
    }

    private static final class Window {
        final long startSec;
        final AtomicInteger count;

        Window(long startSec, AtomicInteger count) {
            this.startSec = startSec;
            this.count = count;
        }
    }
}
