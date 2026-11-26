package com.umar.integration.api.rest;

import com.umar.payload.request.integration.ApprovalDecisionRequest;
import com.umar.payload.request.integration.ConfigureConnectorRequest;
import com.umar.payload.request.integration.InboundDataRequest;
import com.umar.payload.request.integration.OnboardPartnerRequest;
import com.umar.integration.service.ApprovalService;
import com.umar.integration.service.ConnectorService;
import com.umar.integration.service.PartnerService;
import com.umar.integration.service.UsageService;
import com.umar.integration.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationGovernanceController {

    private final ConnectorService connectorService;
    private final UsageService usageService;
    private final ApprovalService approvalService;
    private final PartnerService partnerService;
    private final JsonUtils jsonUtils;

    // INT-08: Configure Connector
    @PostMapping("/connectors")
    public ResponseEntity<Map<String, Object>> configureConnector(
            @Valid @RequestBody ConfigureConnectorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(connectorService.configureConnector(request));
    }

    // INT-09: Receive Inbound Data
    @PostMapping("/inbound/{connector}")
    public ResponseEntity<Map<String, Object>> receiveInboundData(
            @PathVariable String connector,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader Map<String, String> rawHeaders,
            @RequestBody String rawPayload,
            HttpServletRequest servletRequest) {
        InboundDataRequest parsed = jsonUtils.fromJson(rawPayload, InboundDataRequest.class);
        if (parsed == null || parsed.getRecords() == null || parsed.getRecords().isEmpty()) {
            throw com.umar.integration.error.HubException.validation("records must not be empty");
        }
        Map<String, String> normalizedHeaders = new LinkedHashMap<>();
        rawHeaders.forEach((k, v) -> normalizedHeaders.put(k.toLowerCase(Locale.ROOT), v));
        String remoteIp = servletRequest.getHeader("X-Forwarded-For") != null
                ? servletRequest.getHeader("X-Forwarded-For").split(",")[0].trim()
                : servletRequest.getRemoteAddr();

        ConnectorService.InboundResult result = connectorService.receiveInboundData(
                connector, idempotencyKey, rawPayload, parsed, normalizedHeaders, remoteIp);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    // INT-10: Get Partner Usage
    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> getPartnerUsage(
            @RequestParam(required = false) String partnerId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "DAY") String resolution,
            @RequestParam(defaultValue = "webhooks,inbound,extensions,quotas") String sections,
            @RequestParam(required = false) String subscriptionId,
            @RequestParam(required = false) String connectorId,
            @RequestParam(required = false) String extensionId,
            @RequestParam(defaultValue = "PRODUCTION") String environment,
            @RequestParam(defaultValue = "JSON") String format) {
        return ResponseEntity.ok(usageService.getPartnerUsage(
                partnerId, from, to, resolution, sections, subscriptionId,
                connectorId, extensionId, environment, format));
    }

    // INT-15: Decide Integration Approval
    @PutMapping("/approvals/{approvalId}/decision")
    public ResponseEntity<Map<String, Object>> decideApproval(
            @PathVariable String approvalId,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        return ResponseEntity.ok(approvalService.decide(approvalId, request));
    }

    // INT-16: Onboard Partner
    @PostMapping("/partners")
    public ResponseEntity<Map<String, Object>> onboardPartner(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OnboardPartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(partnerService.onboardPartner(idempotencyKey, request));
    }

    @GetMapping("/partners")
    public ResponseEntity<List<Map<String, Object>>> listPartners() {
        return ResponseEntity.ok(partnerService.listPartners());
    }
}
