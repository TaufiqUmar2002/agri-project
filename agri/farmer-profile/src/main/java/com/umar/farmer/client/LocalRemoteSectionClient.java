package com.umar.farmer.client;

import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LocalRemoteSectionClient implements RemoteSectionClient {

    @Setter
    private volatile boolean payoutsUnavailable = false;

    @Override
    public Map<String, Object> fetchPlots(UUID farmerId) {
        return Map.of("status", "EMPTY", "items", List.of());
    }

    @Override
    public Map<String, Object> fetchPolicies(UUID farmerId) {
        return Map.of("status", "EMPTY", "items", List.of());
    }

    @Override
    public Map<String, Object> fetchPayouts(UUID farmerId, int limit) {
        if (payoutsUnavailable) {
            return Map.of("status", "UNAVAILABLE", "retryAfterSeconds", 30);
        }
        return Map.of("status", "EMPTY", "items", List.of());
    }
}
