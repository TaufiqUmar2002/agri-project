package com.umar.audit.controller;

import com.umar.audit.service.AuditGovernanceService;
import com.umar.payload.request.audit.*;
import com.umar.payload.response.audit.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditGovernanceService governanceService;

    @GetMapping("/decisions/{claimId}")
    public ResponseEntity<ClaimTraceResponse> traceDecision(
            @PathVariable("claimId") String claimId,
            @RequestParam(value = "format", required = false) String format) {
        return ResponseEntity.ok(governanceService.traceClaimDecision(claimId, format));
    }

    @PostMapping("/reports/regulatory")
    public ResponseEntity<RegulatoryReportResponse> regulatoryReport(@Valid @RequestBody RegulatoryReportRequest req) {
        RegulatoryReportResponse resp = governanceService.handleRegulatoryReport(req);
        HttpStatus status = "DRAFT".equals(resp.getStatus()) || "SCHEDULED".equals(resp.getStatus())
                ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(resp);
    }

    @GetMapping("/models")
    public ResponseEntity<ModelRegisterResponse> listModels(
            @RequestParam(value = "asOf", required = false) String asOf,
            @RequestParam(value = "approvalStatus", required = false) String approvalStatus) {
        return ResponseEntity.ok(governanceService.listModels(asOf, approvalStatus));
    }

    @GetMapping("/records")
    public ResponseEntity<AuditRecordsResponse> searchRecords(
            @RequestParam(value = "claimId", required = false) String claimId,
            @RequestParam(value = "farmerId", required = false) String farmerId,
            @RequestParam(value = "subjectId", required = false) String subjectId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ResponseEntity.ok(governanceService.searchRecords(claimId, farmerId, subjectId, from, to));
    }

    @GetMapping("/integrity")
    public ResponseEntity<IntegrityStatusResponse> getIntegrity(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        IntegrityStatusResponse resp = governanceService.getIntegrityStatus(ifNoneMatch);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, "\"int-" + resp.getOverall() + "\"").body(resp);
    }

    @PostMapping("/integrity/verifications")
    public ResponseEntity<Map<String, Object>> startVerification(@Valid @RequestBody StartVerificationRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(governanceService.startVerification(req));
    }

    @GetMapping("/reports")
    public ResponseEntity<ReportsListResponse> listReports(
            @RequestParam(value = "reportId", required = false) String reportId,
            @RequestParam(value = "download", defaultValue = "false") boolean download) {
        return ResponseEntity.ok(governanceService.listReports(reportId, download));
    }

    @GetMapping("/findings")
    public ResponseEntity<FindingsListResponse> listFindings(
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(governanceService.listFindings(status));
    }

    @PutMapping("/findings/{id}/disposition")
    public ResponseEntity<FindingDispositionResponse> disposeFinding(
            @PathVariable("id") String findingId,
            @RequestBody FindingDispositionRequest req) {
        FindingDispositionResponse resp = governanceService.disposeFinding(findingId, req);
        HttpStatus status = "PENDING_APPROVAL".equals(resp.getApprovalStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(resp);
    }

    @PostMapping("/holds")
    public ResponseEntity<LegalHoldResponse> manageHolds(@Valid @RequestBody LegalHoldRequest req) {
        LegalHoldResponse resp = governanceService.manageLegalHold(req);
        HttpStatus status = switch (resp.getStatus()) {
            case "ACTIVE" -> HttpStatus.CREATED;
            case "PENDING_RELEASE" -> HttpStatus.ACCEPTED;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(resp);
    }
}
