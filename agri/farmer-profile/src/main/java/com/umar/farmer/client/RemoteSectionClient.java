package com.umar.farmer.client;

import java.util.Map;
import java.util.UUID;

public interface RemoteSectionClient {
    Map<String, Object> fetchPlots(UUID farmerId);
    Map<String, Object> fetchPolicies(UUID farmerId);
    Map<String, Object> fetchPayouts(UUID farmerId, int limit);
}
