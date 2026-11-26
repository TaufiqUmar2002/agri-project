package com.umar.payout.controller;

import com.umar.payload.request.payout.FallbackPayoutRequest;
import com.umar.payload.request.payout.ImportPayoutStatementRequest;
import com.umar.payload.request.payout.UpdateProviderStatusRequest;
import com.umar.payload.response.payout.*;
import com.umar.payout.service.PayoutExecutionService;
import com.umar.payout.service.PayoutReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutExecutionService executionService;
    private final PayoutReconciliationService reconciliationService;

    @GetMapping
    public ResponseEntity<PayoutListResponse> listPayouts(
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String claimId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(executionService.listPayouts(queue, status, claimId, page, size));
    }

    @GetMapping("/settlements")
    public ResponseEntity<PayoutSettlementResponse> settlements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "JSON") String format) {
        return ResponseEntity.ok(reconciliationService.getSettlements(from, to, format));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayoutDetailResponse> getPayout(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        PayoutDetailResponse resp = executionService.getPayout(id);
        String etag = "\"" + resp.getVersion() + "\"";
        if (etag.equals(ifNoneMatch) || String.valueOf(resp.getVersion()).equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok().eTag(etag).body(resp);
    }

    @PostMapping("/callback/{provider}")
    public ResponseEntity<Map<String, Object>> callback(
            @PathVariable String provider,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestBody String rawBody) {
        return ResponseEntity.ok(executionService.handleProviderCallback(provider, rawBody, signature, timestamp, nonce));
    }

    @PostMapping("/{id}/fallback")
    public ResponseEntity<PayoutDetailResponse> fallback(
            @PathVariable String id,
            @Valid @RequestBody FallbackPayoutRequest req) {
        return ResponseEntity.ok(executionService.fallbackPayout(id, req));
    }

    @PostMapping("/statements")
    public ResponseEntity<PayoutStatementImportResponse> importStatements(@Valid @RequestBody ImportPayoutStatementRequest req) {
        return ResponseEntity.ok(reconciliationService.importStatement(req));
    }

    @PutMapping("/providers/{provider}/status")
    public ResponseEntity<ProviderStatusResponse> updateProviderStatus(
            @PathVariable String provider,
            @Valid @RequestBody UpdateProviderStatusRequest req) {
        return ResponseEntity.ok(reconciliationService.updateProviderStatus(provider, req));
    }
}
