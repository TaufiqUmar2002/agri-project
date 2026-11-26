package com.umar.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Centralized OpenFeign gateway client that routes all cross-service and Vault
 * calls through integration-service instead of direct service-to-service URLs.
 */
@FeignClient(
        name = "integration-hub-service",
        url = "${agreeshield.integration.url:http://localhost:8089}",
        contextId = "integrationHubFeignClient"
)
public interface IntegrationHubFeignClient {

    @PostMapping("/api/extensions/points/register")
    Map<String, Object> registerExtensionPoints(@RequestBody Map<String, Object> request);

    @PostMapping("/api/extensions/invoke")
    Map<String, Object> invokeExtension(@RequestBody Map<String, Object> request);

    @PostMapping("/api/integrations/bridge/vault/encrypt")
    Map<String, String> vaultEncrypt(@RequestBody Map<String, String> request);

    @PostMapping("/api/integrations/bridge/vault/decrypt")
    Map<String, String> vaultDecrypt(@RequestBody Map<String, String> request);

    @PostMapping("/api/integrations/bridge/vault/tokenize")
    Map<String, Object> vaultTokenize(@RequestBody Map<String, Object> request);

    @PostMapping("/api/integrations/bridge/vault/detokenize")
    Map<String, Object> vaultDetokenize(@RequestBody Map<String, Object> request);

    @GetMapping("/api/integrations/bridge/auth/users/{userId}")
    Map<String, Object> getAuthUserContext(@PathVariable("userId") String userId);

    @GetMapping("/api/integrations/bridge/farmers/{farmerId}")
    Map<String, Object> getFarmerContext(@PathVariable("farmerId") String farmerId);

    @GetMapping("/api/integrations/bridge/plots/{plotId}")
    Map<String, Object> getPlotDetails(@PathVariable("plotId") String plotId);

    @GetMapping("/api/integrations/bridge/policies/{policyId}")
    Map<String, Object> getPolicySummary(@PathVariable("policyId") String policyId);

    @GetMapping("/api/integrations/bridge/claims/{claimId}")
    Map<String, Object> getClaimSummary(@PathVariable("claimId") String claimId);

    @PostMapping("/api/integrations/bridge/notifications/messages")
    Map<String, Object> sendNotification(@RequestBody Map<String, Object> request);

    @PostMapping("/api/integrations/bridge/audit/events")
    Map<String, Object> recordAuditEvent(@RequestBody Map<String, Object> request);

    @PostMapping("/api/integrations/bridge/dispatch/{targetService}")
    Map<String, Object> dispatchToService(
            @PathVariable("targetService") String targetService,
            @RequestBody Map<String, Object> payload);
}
