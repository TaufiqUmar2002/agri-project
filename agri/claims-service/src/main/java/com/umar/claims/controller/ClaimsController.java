package com.umar.claims.controller;

import com.umar.claims.service.ClaimsApiService;
import com.umar.payload.request.claims.*;
import com.umar.payload.response.claims.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimsController {

    private final ClaimsApiService apiService;

    @GetMapping
    public ResponseEntity<ClaimsListResponse> listClaims(
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String farmerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(apiService.listClaims(queue, status, farmerId, page, size));
    }

    @GetMapping("/reports/reconciliation")
    public ResponseEntity<ClaimsReconciliationReportResponse> reconciliationReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String regionCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(apiService.getReconciliationReport(from, to, regionCode, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimDetailResponse> getClaim(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ClaimDetailResponse resp = apiService.getClaim(id);
        String etag = "\"" + resp.getVersion() + "\"";
        if (etag.equals(ifNoneMatch) || String.valueOf(resp.getVersion()).equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok().eTag(etag).body(resp);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ClaimActionResponse> approveClaim(
            @PathVariable String id,
            @Valid @RequestBody ApproveClaimRequest req) {
        return ResponseEntity.ok(apiService.approveClaim(id, req));
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<DisputeResponse> createDispute(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateDisputeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiService.createDispute(id, req, idempotencyKey));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ClaimActionResponse> retryClaim(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RetryClaimRequest req) {
        return ResponseEntity.ok(apiService.retryClaim(id, req, idempotencyKey));
    }

    @PostMapping("/{id}/confirm-payout")
    public ResponseEntity<ClaimActionResponse> confirmPayout(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Delegation-Id", required = false) String delegationHeader,
            @Valid @RequestBody ConfirmPayoutRequest req) {
        return ResponseEntity.ok(apiService.confirmPayout(id, req, delegationHeader, idempotencyKey));
    }

    @PutMapping("/{id}/disputes/{disputeId}/resolution")
    public ResponseEntity<DisputeResponse> resolveDispute(
            @PathVariable String id,
            @PathVariable String disputeId,
            @Valid @RequestBody ResolveDisputeRequest req) {
        return ResponseEntity.ok(apiService.resolveDispute(id, disputeId, req));
    }
}
