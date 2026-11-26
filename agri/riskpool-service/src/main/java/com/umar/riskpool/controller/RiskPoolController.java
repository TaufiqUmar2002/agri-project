package com.umar.riskpool.controller;

import com.umar.enums.riskpool.LedgerViewType;
import com.umar.payload.request.riskpool.*;
import com.umar.payload.response.riskpool.*;
import com.umar.riskpool.service.RiskPoolAnalyticsService;
import com.umar.riskpool.service.RiskPoolLedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pools")
@RequiredArgsConstructor
public class RiskPoolController {

    private final RiskPoolLedgerService ledgerService;
    private final RiskPoolAnalyticsService analyticsService;

    @PostMapping
    public ResponseEntity<PoolResponse> createPool(@Valid @RequestBody CreatePoolRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.createPool(req));
    }

    @PostMapping("/{id}/treaties")
    public ResponseEntity<TreatyResponse> createTreaty(@PathVariable String id, @Valid @RequestBody CreateTreatyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.createTreaty(id, req));
    }

    @PutMapping("/{id}/alerts")
    public ResponseEntity<PoolAlertsResponse> configureAlerts(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ConfigurePoolAlertsRequest req) {
        PoolAlertsResponse resp = analyticsService.configureAlerts(id, req, ifMatch);
        return ResponseEntity.ok().eTag("\"" + resp.getVersion() + "\"").body(resp);
    }

    @PostMapping("/{id}/journal")
    public ResponseEntity<ManualJournalResponse> postManualJournal(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PostManualJournalRequest req) {
        return ResponseEntity.accepted().body(ledgerService.postManualJournal(id, req, idempotencyKey));
    }

    @GetMapping("/{id}/exposure")
    public ResponseEntity<PoolExposureResponse> getExposure(
            @PathVariable String id,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Double stressPercent) {
        return ResponseEntity.ok(analyticsService.getExposure(id, regionCode, stressPercent));
    }

    @GetMapping("/{id}/bordereaux")
    public ResponseEntity<BordereauxResponse> getBordereaux(
            @PathVariable String id,
            @RequestParam String type,
            @RequestParam String period,
            @RequestParam(required = false) String treatyId) {
        return ResponseEntity.ok(analyticsService.getBordereaux(id, type, period, treatyId));
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<LedgerViewResponse> getLedger(
            @PathVariable String id,
            @RequestParam(defaultValue = "TRIAL_BALANCE") LedgerViewType view,
            @RequestParam(defaultValue = "false") boolean verify) {
        return ResponseEntity.ok(analyticsService.getLedger(id, view, verify));
    }

    @PostMapping("/{id}/periods/{period}/close")
    public ResponseEntity<PeriodCloseResponse> closePeriod(
            @PathVariable String id,
            @PathVariable String period,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ClosePeriodRequest req) {
        return ResponseEntity.accepted().body(ledgerService.closeOrReopenPeriod(id, period, req, idempotencyKey));
    }

    @PutMapping("/approvals/{approvalId}/decision")
    public ResponseEntity<PoolApprovalResponse> decideApproval(
            @PathVariable String approvalId,
            @Valid @RequestBody DecidePoolApprovalRequest req) {
        return ResponseEntity.ok(ledgerService.decideApproval(approvalId, req));
    }
}
