package com.umar.plot.service.support;

import com.umar.plot.exception.PlotApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlotRateLimitService {
    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public void check(String key, int max, long windowSec) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(windowSec);
        Deque<Instant> q = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
            if (q.size() >= max) {
                long retryAfter = Math.max(1L, windowSec - (now.getEpochSecond() - q.peekFirst().getEpochSecond()));
                throw new PlotApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "Too many requests. Try again later", Map.of("retryAfterSeconds", retryAfter));
            }
            q.addLast(now);
        }
    }
}
