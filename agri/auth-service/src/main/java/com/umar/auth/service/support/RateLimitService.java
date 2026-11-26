package com.umar.auth.service.support;

import com.umar.auth.exception.AuthApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public void checkAndIncrement(String bucketKey, int maxRequests, long windowSeconds) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(windowSeconds);
        Deque<Instant> queue = buckets.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
                queue.pollFirst();
            }
            if (queue.size() >= maxRequests) {
                long retryAfter = Math.max(1L, windowSeconds - (now.getEpochSecond() - queue.peekFirst().getEpochSecond()));
                throw new AuthApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "Too many attempts. Try again later",
                        Map.of("retryAfterSeconds", retryAfter));
            }
            queue.addLast(now);
        }
    }

    public void clearAll() {
        buckets.clear();
    }
}
