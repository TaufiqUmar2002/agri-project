package com.umar.integration;

import java.util.List;
import java.util.Map;

/**
 * Implemented by each microservice module to declare its extension hook points
 * with integration-service cleanly and consistently.
 */
public interface ExtensionHookProvider {

    String serviceName();

    List<ExtensionHookDescriptor> extensionPoints();

    default Map<String, Object> executeLocalHookFallback(String hookId, Map<String, Object> input) {
        return Map.of("status", "NO_EXTENSION", "allowed", true, "hookId", hookId);
    }

    record ExtensionHookDescriptor(
            String hookId,
            String type,
            String contractVersion,
            String description,
            boolean protectedPoint
    ) {}
}
