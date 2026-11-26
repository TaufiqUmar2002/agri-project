package com.umar.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Unified hub-routing gateway used by all microservices to call peer services,
 * Vault, and Extension Hooks strictly via integration-service (never directly).
 */
@Slf4j
@Service
public class IntegrationPeerClient {

    private final ObjectProvider<IntegrationHubFeignClient> hubClientProvider;
    private final ObjectProvider<ExtensionHookCoordinator> hookCoordinatorProvider;
    private final ObjectProvider<com.umar.logging.CentralLoggingService> loggingProvider;

    @Value("${agreeshield.integration.mode:local}")
    private String mode;

    public IntegrationPeerClient(
            ObjectProvider<IntegrationHubFeignClient> hubClientProvider,
            ObjectProvider<ExtensionHookCoordinator> hookCoordinatorProvider,
            ObjectProvider<com.umar.logging.CentralLoggingService> loggingProvider) {
        this.hubClientProvider = hubClientProvider;
        this.hookCoordinatorProvider = hookCoordinatorProvider;
        this.loggingProvider = loggingProvider;
    }

    private void logExternal(String svc, String op, String outcome) {
        com.umar.logging.CentralLoggingService l = loggingProvider != null ? loggingProvider.getIfAvailable() : null;
        if (l != null) {
            l.externalCall(svc, op, outcome);
        }
    }

    public boolean isFeignEnabled() {
        return "feign".equalsIgnoreCase(mode) && hubClientProvider.getIfAvailable() != null;
    }

    public Optional<Map<String, Object>> getAuthUser(String userId) {
        if (!isFeignEnabled()) {
            logExternal("integration-service", "auth.getUser:" + userId, "LOCAL_FALLBACK");
            return Optional.empty();
        }
        try {
            Map<String, Object> res = hubClientProvider.getObject().getAuthUserContext(userId);
            logExternal("integration-service", "auth.getUser:" + userId, "FEIGN_OK");
            return Optional.ofNullable(res);
        } catch (Exception ex) {
            log.debug("Hub call auth/users/{} fallback: {}", userId, ex.getMessage());
            logExternal("integration-service", "auth.getUser:" + userId, "FALLBACK:" + ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> getFarmer(String farmerId) {
        if (!isFeignEnabled()) return Optional.empty();
        try {
            return Optional.ofNullable(hubClientProvider.getObject().getFarmerContext(farmerId));
        } catch (Exception ex) {
            log.debug("Hub call farmers/{} fallback: {}", farmerId, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> getPlot(String plotId) {
        if (!isFeignEnabled()) return Optional.empty();
        try {
            return Optional.ofNullable(hubClientProvider.getObject().getPlotDetails(plotId));
        } catch (Exception ex) {
            log.debug("Hub call plots/{} fallback: {}", plotId, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> getPolicy(String policyId) {
        if (!isFeignEnabled()) return Optional.empty();
        try {
            return Optional.ofNullable(hubClientProvider.getObject().getPolicySummary(policyId));
        } catch (Exception ex) {
            log.debug("Hub call policies/{} fallback: {}", policyId, ex.getMessage());
            return Optional.empty();
        }
    }

    public Map<String, Object> invokeExtension(String hookId, Map<String, Object> input) {
        ExtensionHookCoordinator coord = hookCoordinatorProvider.getIfAvailable();
        if (coord != null) {
            return coord.invokeHook(hookId, input);
        }
        return Map.of("status", "NO_EXTENSION", "allowed", true);
    }

    public Optional<Map<String, Object>> dispatch(String targetService, Map<String, Object> payload) {
        if (!isFeignEnabled()) return Optional.empty();
        try {
            return Optional.ofNullable(hubClientProvider.getObject().dispatchToService(targetService, payload));
        } catch (Exception ex) {
            log.debug("Hub dispatch {} fallback: {}", targetService, ex.getMessage());
            return Optional.empty();
        }
    }
}
