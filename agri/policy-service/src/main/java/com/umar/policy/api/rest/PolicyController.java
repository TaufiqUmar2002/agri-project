package com.umar.policy.api.rest;

import com.umar.payload.request.policy.*;
import com.umar.payload.response.policy.*;
import com.umar.policy.service.PolicyLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyLifecycleService lifecycleService;

    @PostMapping("/api/quotes")
    public ResponseEntity<QuoteResponse> generateQuote(
            @Valid @RequestBody GenerateQuoteRequest request,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(lifecycleService.generateQuote(request, delegationHeader, idempotencyKey));
    }

    @PostMapping("/api/policies")
    public ResponseEntity<PolicyResponse> issuePolicy(
            @Valid @RequestBody IssuePolicyRequest request,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lifecycleService.issuePolicy(request, delegationHeader, idempotencyKey));
    }

    @PostMapping("/api/policies/{id}/activate")
    public ResponseEntity<PolicyResponse> activatePolicy(
            @PathVariable String id,
            @Valid @RequestBody ActivatePolicyRequest request) {
        return ResponseEntity.ok(lifecycleService.activatePolicy(id, request));
    }

    @GetMapping("/api/policies/{id}")
    public ResponseEntity<PolicyResponse> getPolicy(
            @PathVariable String id,
            @RequestParam(required = false) String include,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader) {
        PolicyResponse resp = lifecycleService.getPolicy(id, include, delegationHeader);
        return ResponseEntity.ok()
                .eTag("\"" + resp.getVersion() + "\"")
                .body(resp);
    }

    @PostMapping("/api/policies/{id}/cancel")
    public ResponseEntity<CancelPolicyResponse> cancelPolicy(
            @PathVariable String id,
            @Valid @RequestBody CancelPolicyRequest request,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(lifecycleService.cancelPolicy(id, request, stepUpToken, idempotencyKey));
    }

    @GetMapping("/api/policies/renewals")
    public ResponseEntity<RenewalsResponse> listRenewals(
            @RequestParam(required = false) Integer daysAhead,
            @RequestParam(required = false) String farmerId,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String cropCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader) {
        return ResponseEntity.ok(lifecycleService.listUpcomingRenewals(
                daysAhead, farmerId, regionCode, cropCode, status, page, size, delegationHeader));
    }
}
