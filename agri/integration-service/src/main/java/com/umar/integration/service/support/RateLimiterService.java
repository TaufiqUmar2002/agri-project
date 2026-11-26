package com.umar.integration.service.support;

import com.umar.integration.error.HubException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterService {

    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public void checkAndConsume(String bucketKey, int maxRequests, Duration window, String message) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Deque<Instant> deque = windows.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                Instant oldest = deque.peekFirst();
                long retryAfter = oldest == null ? window.toSeconds() : Math.max(1L, Duration.between(cutoff, oldest).toSeconds());
                throw HubException.rateLimited(retryAfter, message);
            }
            deque.addLast(now);
        }
    }

    public boolean tryConsume(String bucketKey, int maxRequests, Duration window) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Deque<Instant> deque = windows.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    public void clear() {
        windows.clear();
    }
}
