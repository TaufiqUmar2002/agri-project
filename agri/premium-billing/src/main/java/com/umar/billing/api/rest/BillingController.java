package com.umar.billing.api.rest;

import com.umar.billing.service.BillingCoreService;
import com.umar.billing.service.BillingGovernanceService;
import com.umar.payload.request.billing.*;
import com.umar.payload.response.billing.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingCoreService coreService;
    private final BillingGovernanceService governanceService;

    @PostMapping("/invoices")
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coreService.createInvoice(request, idempotencyKey));
    }

    @PostMapping("/pay")
    public ResponseEntity<PaymentAttemptResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(coreService.initiatePayment(request, delegationHeader, idempotencyKey));
    }

    @PostMapping("/callback/{provider}")
    public ResponseEntity<Map<String, Object>> providerCallback(
            @PathVariable String provider,
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(coreService.processCallback(
                provider, rawBody, signature, timestamp, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/instalments")
    public ResponseEntity<InstalmentPlanResponse> configureInstalmentPlan(
            @Valid @RequestBody ConfigureInstalmentPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(governanceService.configureInstalmentPlan(request));
    }

    @PostMapping("/sponsors/{id}/batch")
    public ResponseEntity<SponsorBatchResponse> createSponsorBatch(
            @PathVariable String id,
            @Valid @RequestBody CreateSponsorBatchRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coreService.createSponsorBatch(id, request, idempotencyKey));
    }

    @PostMapping("/refunds")
    public ResponseEntity<RefundResponse> requestRefund(
            @Valid @RequestBody RequestRefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(governanceService.requestRefund(request, idempotencyKey));
    }

    @GetMapping("/invoices/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @PathVariable String id,
            @RequestParam(required = false) String include,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader) {
        InvoiceResponse resp = coreService.getInvoice(id, include, delegationHeader);
        return ResponseEntity.ok()
                .eTag("\"" + resp.getVersion() + "\"")
                .body(resp);
    }

    @PostMapping("/payments/offline")
    public ResponseEntity<OfflinePaymentResponse> recordOfflinePayment(
            @Valid @RequestBody RecordOfflinePaymentRequest request,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        OfflinePaymentResponse resp = coreService.recordOfflinePayment(request, delegationHeader, idempotencyKey);
        HttpStatus status = "PENDING_APPROVAL".equals(resp.getStatus()) ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resp);
    }

    @PutMapping("/approvals/{approvalId}/decision")
    public ResponseEntity<BillingApprovalResponse> decideApproval(
            @PathVariable String approvalId,
            @Valid @RequestBody BillingApprovalDecisionRequest request) {
        return ResponseEntity.ok(governanceService.decideApproval(approvalId, request));
    }

    @PostMapping("/reconciliation/import")
    public ResponseEntity<ReconciliationRunResponse> importStatement(
            @Valid @RequestBody ImportStatementRequest request) {
        return ResponseEntity.ok(governanceService.importStatement(request));
    }
}
