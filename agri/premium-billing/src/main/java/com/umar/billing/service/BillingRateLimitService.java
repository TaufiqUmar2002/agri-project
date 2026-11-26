package com.umar.billing.service;

import com.umar.billing.exception.BillingApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BillingRateLimitService {
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public void check(String key, int maxRequests, long windowSeconds) {
        long nowSec = Instant.now().getEpochSecond();
        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || nowSec - existing.windowStartSec >= windowSeconds) {
                return new WindowCounter(nowSec, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (counter.count.get() > maxRequests) {
            long retryAfter = Math.max(1L, windowSeconds - (nowSec - counter.windowStartSec));
            throw new BillingApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many requests", Map.of("retryAfterSeconds", retryAfter));
        }
    }

    public void reset() {
        counters.clear();
    }

    private record WindowCounter(long windowStartSec, AtomicInteger count) {}
}
