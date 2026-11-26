package com.umar.claims.service.support;

import com.umar.claims.exception.ClaimsApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClaimsRateLimitService {
    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public void check(String key, int limit, int windowSeconds) {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        Deque<Instant> q = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
            if (q.size() >= limit) {
                throw new ClaimsApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests");
            }
            q.addLast(Instant.now());
        }
    }
}
