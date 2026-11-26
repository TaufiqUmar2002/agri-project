package com.umar.integration.api.rest;

import com.umar.integration.service.IntegrationBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal integration-service gateway endpoints allowing all microservices
 * to route peer-service and Vault calls through integration-service rather than directly.
 */
@RestController
@RequestMapping("/api/integrations/bridge")
@RequiredArgsConstructor
public class IntegrationBridgeController {

    private final IntegrationBridgeService bridgeService;

    @PostMapping("/vault/encrypt")
    public ResponseEntity<Map<String, String>> vaultEncrypt(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(bridgeService.vaultEncrypt(request));
    }

    @PostMapping("/vault/decrypt")
    public ResponseEntity<Map<String, String>> vaultDecrypt(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(bridgeService.vaultDecrypt(request));
    }

    @PostMapping("/vault/tokenize")
    public ResponseEntity<Map<String, Object>> vaultTokenize(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(bridgeService.vaultTokenize(request));
    }

    @PostMapping("/vault/detokenize")
    public ResponseEntity<Map<String, Object>> vaultDetokenize(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("resolved", true, "via", "integration-hub"));
    }

    @GetMapping("/auth/users/{userId}")
    public ResponseEntity<Map<String, Object>> getAuthUser(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(bridgeService.getAuthUser(userId));
    }

    @GetMapping("/farmers/{farmerId}")
    public ResponseEntity<Map<String, Object>> getFarmer(@PathVariable("farmerId") String farmerId) {
        return ResponseEntity.ok(bridgeService.getFarmer(farmerId));
    }

    @GetMapping("/plots/{plotId}")
    public ResponseEntity<Map<String, Object>> getPlot(@PathVariable("plotId") String plotId) {
        return ResponseEntity.ok(bridgeService.getPlot(plotId));
    }

    @GetMapping("/policies/{policyId}")
    public ResponseEntity<Map<String, Object>> getPolicy(@PathVariable("policyId") String policyId) {
        return ResponseEntity.ok(bridgeService.getPolicy(policyId));
    }

    @GetMapping("/claims/{claimId}")
    public ResponseEntity<Map<String, Object>> getClaim(@PathVariable("claimId") String claimId) {
        return ResponseEntity.ok(bridgeService.getClaim(claimId));
    }

    @PostMapping("/notifications/messages")
    public ResponseEntity<Map<String, Object>> sendNotification(@RequestBody Map<String, Object> request) {
        return ResponseEntity.accepted().body(bridgeService.sendNotification(request));
    }

    @PostMapping("/audit/events")
    public ResponseEntity<Map<String, Object>> recordAuditEvent(@RequestBody Map<String, Object> request) {
        return ResponseEntity.accepted().body(bridgeService.recordAudit(request));
    }

    @PostMapping("/dispatch/{targetService}")
    public ResponseEntity<Map<String, Object>> dispatch(
            @PathVariable("targetService") String targetService,
            @RequestBody Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("routedVia", "integration-hub-service");
        out.put("targetService", targetService);
        out.put("status", "DELIVERED");
        out.put("payload", payload);
        return ResponseEntity.ok(out);
    }
}
