package com.umar.policy;

import com.umar.entity.dataProtection.AccessPolicy;
import com.umar.events.VaultEventType;
import com.umar.exception.VaultApiException;
import com.umar.repository.dataProtection.AccessPolicyRepository;
import com.umar.service.VaultEventEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enforces per-policy rate limits and sliding-window anomaly detection (sections 2.1, 4.2, 8.2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitAndAnomalyService {

    private static final long WINDOW_SECONDS = 60;
    private static final int DEFAULT_RPM = 600;
    private static final int ANOMALY_MULTIPLIER = 5;

    private final AccessPolicyRepository accessPolicyRepository;
    private final VaultEventEmitter vaultEventEmitter;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> rollingBaselines = new ConcurrentHashMap<>();

    public void checkAndConsume(String caller, String dataClass, String purpose, String operation, int itemCount) {
        int limitRpm = resolveRateLimitRpm(caller, dataClass, purpose, operation);
        String key = caller + "|" + (dataClass == null ? "*" : dataClass) + "|" + (purpose == null ? "*" : purpose);
        long nowSec = System.currentTimeMillis() / 1000;

        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || nowSec - existing.windowStartSec >= WINDOW_SECONDS) {
                if (existing != null) {
                    updateBaseline(key, existing.count.get());
                }
                return new WindowCounter(nowSec, new AtomicInteger(itemCount));
            }
            existing.count.addAndGet(itemCount);
            return existing;
        });

        int observed = counter.count.get();
        double baseline = rollingBaselines.getOrDefault(key, (double) Math.max(20, limitRpm / 10));

        if (observed > limitRpm) {
            vaultEventEmitter.emit(VaultEventType.ACCESS_ANOMALY, Map.of(
                    "callerService", caller,
                    "dataClass", dataClass == null ? "" : dataClass,
                    "purpose", purpose == null ? "" : purpose,
                    "observedRate", observed,
                    "baselineRate", baseline,
                    "action", "THROTTLED"));
            throw VaultApiException.rateLimited("Policy rate limit exceeded for " + dataClass, 60);
        }

        if (observed > baseline * ANOMALY_MULTIPLIER && observed > 50) {
            vaultEventEmitter.emit(VaultEventType.ACCESS_ANOMALY, Map.of(
                    "callerService", caller,
                    "dataClass", dataClass == null ? "" : dataClass,
                    "purpose", purpose == null ? "" : purpose,
                    "observedRate", observed,
                    "baselineRate", baseline,
                    "action", "ALERTED"));
            log.warn("Unusual vault access rate for {}: observed={} baseline={}", key, observed, baseline);
        }
    }

    public int resolveMaxBatchSize(String caller, String dataClass, String purpose, String operation, int defaultMax) {
        List<AccessPolicy> policies = accessPolicyRepository.findMatchingPolicies(caller, dataClass, purpose, operation);
        if (policies.isEmpty()) {
            return defaultMax;
        }
        for (AccessPolicy p : policies) {
            if (p.getBatch() != null && !p.getBatch().isBlank()) {
                try {
                    return Integer.parseInt(p.getBatch().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return defaultMax;
    }

    private int resolveRateLimitRpm(String caller, String dataClass, String purpose, String operation) {
        if (caller == null || dataClass == null || purpose == null || operation == null) {
            return DEFAULT_RPM;
        }
        List<AccessPolicy> policies = accessPolicyRepository.findMatchingPolicies(caller, dataClass, purpose, operation);
        for (AccessPolicy p : policies) {
            if (p.getRates() != null && !p.getRates().isBlank()) {
                try {
                    return Math.max(1, Integer.parseInt(p.getRates().trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return DEFAULT_RPM;
    }

    private void updateBaseline(String key, int previousWindowCount) {
        rollingBaselines.merge(key, (double) Math.max(1, previousWindowCount),
                (oldVal, newVal) -> oldVal * 0.8 + newVal * 0.2);
    }

    private static final class WindowCounter {
        final long windowStartSec;
        final AtomicInteger count;

        WindowCounter(long windowStartSec, AtomicInteger count) {
            this.windowStartSec = windowStartSec;
            this.count = count;
        }
    }
}
