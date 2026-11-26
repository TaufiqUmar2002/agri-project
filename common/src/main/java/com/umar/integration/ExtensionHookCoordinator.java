package com.umar.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Coordinates registration and invocation of per-service extension hooks
 * through integration-service (using OpenFeign in distributed mode or safe
 * in-memory pass-through in isolated unit tests).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtensionHookCoordinator {

    private final ObjectProvider<IntegrationHubFeignClient> hubClientProvider;
    private final List<ExtensionHookProvider> providers;

    @Value("${agreeshield.integration.mode:local}")
    private String integrationMode;

    @Value("${spring.application.name:unknown-service}")
    private String applicationName;

    @EventListener(ApplicationReadyEvent.class)
    public void registerDeclaredHooksOnStartup() {
        if (providers == null || providers.isEmpty()) {
            return;
        }
        for (ExtensionHookProvider provider : providers) {
            if ("feign".equalsIgnoreCase(integrationMode) && hubClientProvider.getIfAvailable() != null) {
                try {
                    List<Map<String, Object>> points = provider.extensionPoints().stream()
                            .map(p -> Map.<String, Object>of(
                                    "hookId", p.hookId(),
                                    "type", p.type(),
                                    "contractVersion", p.contractVersion(),
                                    "description", p.description(),
                                    "protectedPoint", p.protectedPoint()
                            ))
                            .toList();
                    hubClientProvider.getObject().registerExtensionPoints(Map.of(
                            "serviceName", provider.serviceName(),
                            "points", points
                    ));
                } catch (Exception ex) {
                    log.debug("Extension registration deferred for {}: {}", provider.serviceName(), ex.getMessage());
                }
            }
        }
    }

    public Map<String, Object> invokeHook(String hookId, Map<String, Object> input) {
        if ("feign".equalsIgnoreCase(integrationMode) && hubClientProvider.getIfAvailable() != null) {
            try {
                return hubClientProvider.getObject().invokeExtension(Map.of(
                        "hookId", hookId,
                        "callerService", applicationName,
                        "input", input != null ? input : Map.of()
                ));
            } catch (Exception ex) {
                log.debug("Falling back for hook {} in {}: {}", hookId, applicationName, ex.getMessage());
            }
        }
        for (ExtensionHookProvider p : providers) {
            boolean owns = p.extensionPoints().stream().anyMatch(d -> d.hookId().equals(hookId));
            if (owns) {
                return p.executeLocalHookFallback(hookId, input);
            }
        }
        return Map.of("status", "NO_EXTENSION", "allowed", true, "hookId", hookId);
    }

    public List<ExtensionHookProvider.ExtensionHookDescriptor> getRegisteredDescriptors() {
        List<ExtensionHookProvider.ExtensionHookDescriptor> all = new ArrayList<>();
        for (ExtensionHookProvider p : providers) {
            all.addAll(p.extensionPoints());
        }
        return all;
    }
}
