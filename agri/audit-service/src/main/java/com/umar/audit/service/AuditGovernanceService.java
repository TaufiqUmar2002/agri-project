package com.umar.audit.service;

import com.umar.audit.client.AuditExternalClients;
import com.umar.audit.config.AuditProperties;
import com.umar.audit.domain.*;
import com.umar.audit.exception.AuditApiException;
import com.umar.audit.repository.*;
import com.umar.audit.security.AuditIdentityFilter;
import com.umar.audit.service.support.AuditSupportService;
import com.umar.enums.audit.*;
import com.umar.payload.request.audit.*;
import com.umar.payload.response.audit.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuditGovernanceService {

    private final AuditRecordRepository recordRepository;
    private final AuditCheckpointRepository checkpointRepository;
    private final RegulatoryReportRepository reportRepository;
    private final ModelRegisterRepository modelRepository;
    private final VerificationRunRepository verificationRepository;
    private final AuditFindingRepository findingRepository;
    private final LegalHoldRepository holdRepository;
    private final AuditExternalClients clients;
    private final AuditSupportService support;
    private final AuditIntakeAndKeeperService keeperService;
    private final AuditProperties properties;

    @Transactional
    public ClaimTraceResponse traceClaimDecision(String claimId, String format) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        if (!id.hasAnyRole("COMPLIANCE_OFFICER", "AUDITOR", "REGULATOR_VIEWER")) {
            throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only compliance, auditor, or regulator roles may trace decisions");
        }
        support.appendAccessRecord(id.userId(), "TRACE_DECISION", claimId);

        List<AuditRecordEntity> records = recordRepository.findByClaimIdOrderByOccurredAtAsc(claimId);
        if (records.isEmpty()) {
            throw new AuditApiException(HttpStatus.NOT_FOUND, "CLAIM_TRACE_NOT_FOUND", "No audit trail found for claim " + claimId);
        }

        String policyId = null;
        String plotId = null;
        Map<String, Object> claim = new LinkedHashMap<>();
        Map<String, Object> decision = new LinkedHashMap<>();
        Map<String, Object> evidence = new LinkedHashMap<>();
        Map<String, Object> policy = new LinkedHashMap<>();
        List<Map<String, Object>> payouts = new ArrayList<>();
        List<Map<String, Object>> notifications = new ArrayList<>();

        Set<String> chainsTouched = new HashSet<>();
        for (AuditRecordEntity r : records) {
            chainsTouched.add(r.getChainId());
            if (r.getPolicyId() != null) policyId = r.getPolicyId();
            if (r.getPlotId() != null) plotId = r.getPlotId();
            Map<String, Object> p = support.fromJsonMap(r.getPayloadJson());
            if (r.getEventType().startsWith("claim.")) {
                claim.put(r.getEventType(), p);
                if (r.getEventType().contains("decision")) decision.putAll(p);
            } else if (r.getEventType().startsWith("trigger.") || r.getEventType().startsWith("iot.")) {
                evidence.put(r.getEventType(), p);
                if (p.get("bundleRef") != null && p.get("bundleHash") != null) {
                    boolean valid = clients.verifyEvidenceBundleHash(String.valueOf(p.get("bundleRef")), String.valueOf(p.get("bundleHash")));
                    evidence.put("bundleVerified", valid);
                }
            } else if (r.getEventType().startsWith("policy.")) {
                policy.putAll(p);
            } else if (r.getEventType().startsWith("payout.")) {
                payouts.add(p);
            } else if (r.getEventType().startsWith("notification.")) {
                notifications.add(p);
            }
        }

        boolean chainIntact = true;
        for (String chainId : chainsTouched) {
            if (!verifyChainHashes(chainId)) {
                chainIntact = false;
            }
        }

        List<Map<String, Object>> findings = findingRepository.findByClaimIdOrderByCreatedAtDesc(claimId).stream()
                .map(f -> Map.<String, Object>of("findingId", f.getFindingId(), "ruleCode", f.getRuleCode(),
                        "severity", f.getSeverity().name(), "status", f.getStatus().name()))
                .toList();

        return ClaimTraceResponse.builder()
                .claimId(claimId)
                .policyId(policyId)
                .plotId(plotId)
                .claim(claim)
                .decision(decision)
                .evidence(evidence)
                .policy(policy)
                .payouts(payouts)
                .notifications(notifications)
                .integrity(Map.of("status", chainIntact ? "VERIFIED" : "BROKEN", "chainsChecked", chainsTouched))
                .completeness(Map.of("recordCount", records.size(), "hasDecision", !decision.isEmpty()))
                .findings(findings)
                .packToken("PACK".equalsIgnoreCase(format) ? support.idPrefix("pack_") : null)
                .build();
    }

    @Transactional
    public RegulatoryReportResponse handleRegulatoryReport(RegulatoryReportRequest req) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        if (!id.hasRole("COMPLIANCE_OFFICER")) {
            throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Requires COMPLIANCE_OFFICER role");
        }
        String mode = req.getMode() != null ? req.getMode().toUpperCase(Locale.ROOT) : "ONE_OFF";
        Instant now = Instant.now();

        if ("APPROVE".equals(mode)) {
            RegulatoryReportEntity r = reportRepository.findByReportId(req.getReportId())
                    .orElseThrow(() -> new AuditApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Report not found"));
            if (id.userId().equals(r.getCreatedBy())) {
                throw new AuditApiException(HttpStatus.FORBIDDEN, "APPROVER_NOT_INDEPENDENT", "Creator cannot self-approve regulatory report");
            }
            if (findingRepository.existsByRuleCodeAndStatus("HASH_CHAIN_MISMATCH", FindingStatus.OPEN)) {
                throw new AuditApiException(HttpStatus.CONFLICT, "INTEGRITY_WARNING", "Open hash-chain integrity alert blocks report finalization");
            }
            if (req.getReportHash() != null && !req.getReportHash().equals(r.getReportHash())) {
                throw new AuditApiException(HttpStatus.CONFLICT, "REPORT_CHANGED", "Report hash mismatch");
            }
            r.setApprovedBy(id.userId());
            r.setStatus("FINAL");
            r.setUpdatedAt(now);
            r = reportRepository.save(r);
            support.emitOutbox("audit.report.generated", r.getReportId(),
                    Map.of("reportId", r.getReportId(), "reportType", r.getReportType().name(), "reportHash", r.getReportHash()));
            return toReportResponse(r);
        }

        if ("CANCEL_SCHEDULE".equals(mode)) {
            RegulatoryReportEntity r = reportRepository.findByReportId(req.getReportId())
                    .orElseThrow(() -> new AuditApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Report not found"));
            r.setStatus("CANCELLED");
            r.setUpdatedAt(now);
            return toReportResponse(reportRepository.save(r));
        }

        long regCount = recordRepository.findByChainIdOrderBySeqAsc("REG7").size();
        List<String> cps = checkpointRepository.findByChainIdOrderByToSeqDesc("REG7").stream()
                .map(AuditCheckpointEntity::getCheckpointId).toList();
        String periodFrom = req.getPeriod() != null ? req.getPeriod().getOrDefault("from", "2026-01-01") : "2026-01-01";
        String periodTo = req.getPeriod() != null ? req.getPeriod().getOrDefault("to", "2026-12-31") : "2026-12-31";
        String reportHash = AuditExternalClients.sha256(req.getReportType().name() + "|" + periodFrom + "|" + periodTo + "|" + regCount);
        String scheduleId = "SCHEDULE".equals(mode) ? support.idPrefix("sch_") : null;

        RegulatoryReportEntity entity = reportRepository.save(RegulatoryReportEntity.builder()
                .id(UUID.randomUUID())
                .reportId(support.idPrefix("rep_"))
                .reportType(req.getReportType())
                .format(req.getFormat())
                .status("SCHEDULE".equals(mode) ? "SCHEDULED" : "DRAFT")
                .scheduleId(scheduleId)
                .periodFrom(periodFrom)
                .periodTo(periodTo)
                .filtersJson(support.toJson(req.getFilters() != null ? req.getFilters() : Map.of()))
                .checkpointsJson(support.toJson(cps))
                .rowCountsJson(support.toJson(Map.of("records", regCount)))
                .totalsJson(support.toJson(Map.of("totalAmount", 0)))
                .reportHash(reportHash)
                .createdBy(id.userId())
                .nextRunAt("SCHEDULE".equals(mode) ? now.plus(7, ChronoUnit.DAYS) : null)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return toReportResponse(entity);
    }

    private RegulatoryReportResponse toReportResponse(RegulatoryReportEntity r) {
        return RegulatoryReportResponse.builder()
                .reportId(r.getReportId())
                .reportType(r.getReportType().name())
                .format(r.getFormat())
                .status(r.getStatus())
                .period(Map.of("from", r.getPeriodFrom() != null ? r.getPeriodFrom() : "", "to", r.getPeriodTo() != null ? r.getPeriodTo() : ""))
                .checkpoints(support.fromJsonListString(r.getCheckpointsJson()))
                .integrity(Map.of("verified", !findingRepository.existsByRuleCodeAndStatus("HASH_CHAIN_MISMATCH", FindingStatus.OPEN)))
                .rowCounts(support.fromJsonMap(r.getRowCountsJson()))
                .totals(support.fromJsonMap(r.getTotalsJson()))
                .reportHash(r.getReportHash())
                .scheduleId(r.getScheduleId())
                .nextRunAt(r.getNextRunAt() != null ? r.getNextRunAt().toString() : null)
                .approvals(r.getApprovedBy() != null ? List.of(Map.of("approvedBy", r.getApprovedBy())) : List.of())
                .downloadAvailable("FINAL".equals(r.getStatus()))
                .build();
    }

    @Transactional(readOnly = true)
    public ModelRegisterResponse listModels(String asOfStr, String approvalStatus) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        Instant asOf = asOfStr != null ? Instant.parse(asOfStr) : Instant.now();
        if (asOf.isAfter(Instant.now().plusSeconds(5))) {
            throw new AuditApiException(HttpStatus.UNPROCESSABLE_CONTENT, "AS_OF_INVALID", "Future asOf timestamp is not allowed");
        }
        boolean hideNotes = id.hasRole("ADMIN") && !id.hasAnyRole("COMPLIANCE_OFFICER", "AUDITOR", "REGULATOR_VIEWER");
        List<Map<String, Object>> items = new ArrayList<>();
        for (ModelRegisterEntity m : modelRepository.findAll()) {
            if (m.getEffectiveFrom().isAfter(asOf)) continue;
            if (approvalStatus != null && !approvalStatus.equalsIgnoreCase(m.getApprovalStatus())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("modelId", m.getModelId());
            row.put("version", m.getVersion());
            row.put("component", m.getComponent());
            row.put("approvalStatus", m.getApprovalStatus());
            row.put("ruleHash", m.getRuleHash());
            row.put("approvedBy", m.getApprovedBy());
            if (!hideNotes) {
                row.put("governanceNotes", m.getGovernanceNotes());
            }
            items.add(row);
        }
        return ModelRegisterResponse.builder().asOf(asOf.toString()).items(items).build();
    }

    @Transactional
    public AuditRecordsResponse searchRecords(String claimId, String farmerId, String subjectId, String fromStr, String toStr) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        if (!id.hasAnyRole("COMPLIANCE_OFFICER", "AUDITOR", "SECURITY_OFFICER", "REGULATOR_VIEWER")) {
            throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient role for audit records search");
        }
        String effectiveSubject = subjectId;
        if (farmerId != null && !farmerId.isBlank()) {
            Optional<String> tok = clients.deriveSubjectToken(farmerId);
            if (tok.isEmpty()) {
                support.appendAccessRecord(id.userId(), "SEARCH_RECORDS_ERASED", farmerId);
                return AuditRecordsResponse.builder().items(List.of()).build();
            }
            effectiveSubject = tok.get();
        }
        boolean hasNarrowSubject = claimId != null || effectiveSubject != null;
        Instant from = fromStr != null ? Instant.parse(fromStr) : null;
        Instant to = toStr != null ? Instant.parse(toStr) : null;
        if (!hasNarrowSubject) {
            if (from == null || to == null || ChronoUnit.DAYS.between(from, to) > properties.getRules().getMaxUnscopedSearchDays()) {
                throw new AuditApiException(HttpStatus.BAD_REQUEST, "SEARCH_TOO_BROAD",
                        "Provide subject/claim filter or date range <= " + properties.getRules().getMaxUnscopedSearchDays() + " days");
            }
        }
        support.appendAccessRecord(id.userId(), "SEARCH_RECORDS", claimId != null ? claimId : effectiveSubject);

        List<AuditRecordEntity> raw;
        if (claimId != null) {
            raw = recordRepository.findByClaimIdOrderByOccurredAtAsc(claimId);
        } else if (effectiveSubject != null) {
            raw = recordRepository.findBySubjectIdOrderByOccurredAtAsc(effectiveSubject);
        } else {
            raw = recordRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(from, to);
        }

        boolean securityOnly = id.hasRole("SECURITY_OFFICER") && !id.hasAnyRole("COMPLIANCE_OFFICER", "AUDITOR");
        List<Map<String, Object>> items = new ArrayList<>();
        for (AuditRecordEntity r : raw) {
            if (securityOnly && !"ACC3Y".equals(r.getChainId())) continue;
            items.add(Map.of(
                    "recordId", r.getRecordId(),
                    "chainId", r.getChainId(),
                    "seq", r.getSeq(),
                    "eventType", r.getEventType(),
                    "producer", r.getProducerService(),
                    "occurredAt", r.getOccurredAt().toString(),
                    "recordHash", r.getRecordHash(),
                    "redacted", r.isRedacted()
            ));
        }
        return AuditRecordsResponse.builder().items(items).build();
    }

    @Transactional(readOnly = true)
    public IntegrityStatusResponse getIntegrityStatus(String ifNoneMatch) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        if (!id.hasAnyRole("SECURITY_OFFICER", "COMPLIANCE_OFFICER", "AUDITOR")) {
            throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient role");
        }
        List<Map<String, Object>> chains = new ArrayList<>();
        for (String chainId : List.of("REG7", "EVD7", "OPS13M", "ACC3Y")) {
            Optional<AuditRecordEntity> tail = recordRepository.findFirstByChainIdOrderBySeqDesc(chainId);
            chains.add(Map.of(
                    "chainId", chainId,
                    "headSeq", tail.map(AuditRecordEntity::getSeq).orElse(0L),
                    "headHash", tail.map(AuditRecordEntity::getRecordHash).orElse("GENESIS")
            ));
        }
        boolean broken = findingRepository.existsByRuleCodeAndStatus("HASH_CHAIN_MISMATCH", FindingStatus.OPEN);
        String overall = broken ? "DEGRADED" : "VERIFIED";
        String etag = "\"int-" + overall + "-" + recordRepository.count() + "\"";
        if (etag.equals(ifNoneMatch)) {
            throw new AuditApiException(HttpStatus.NOT_MODIFIED, "NOT_MODIFIED", "Not modified");
        }
        Optional<VerificationRunEntity> lastRun = verificationRepository.findFirstByOrderByCompletedAtDesc();
        return IntegrityStatusResponse.builder()
                .overall(overall)
                .asOf(Instant.now().toString())
                .chains(chains)
                .intake(Map.of("totalRecords", recordRepository.count()))
                .keys(Map.of("activeKeyId", properties.getRules().getSigningKeyId()))
                .alerts(broken ? List.of(Map.of("ruleCode", "HASH_CHAIN_MISMATCH", "status", "OPEN")) : List.of())
                .run(lastRun.map(r -> Map.<String, Object>of("runId", r.getRunId(), "status", r.getStatus(), "checkedRecords", r.getCheckedRecords())).orElse(null))
                .build();
    }

    @Transactional
    public Map<String, Object> startVerification(StartVerificationRequest req) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        if (!id.hasAnyRole("SECURITY_OFFICER", "COMPLIANCE_OFFICER", "AUDITOR")) {
            throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient role");
        }
        List<String> targetChains = req.getChainId() != null
                ? List.of(req.getChainId())
                : List.of("REG7", "EVD7", "OPS13M", "ACC3Y");

        long checked = 0;
        long mismatches = 0;
        AuditRecordEntity mismatchRecord = null;

        for (String chainId : targetChains) {
            List<AuditRecordEntity> recs = recordRepository.findByChainIdOrderBySeqAsc(chainId);
            String expectedPrev = "GENESIS_" + chainId;
            for (AuditRecordEntity r : recs) {
                checked++;
                String recomputed = support.computeRecordHash(chainId, r.getSeq(), expectedPrev, r.getPayloadJson());
                if (!expectedPrev.equals(r.getPrevHash()) || !recomputed.equals(r.getRecordHash())) {
                    mismatches++;
                    if (mismatchRecord == null) mismatchRecord = r;
                }
                expectedPrev = r.getRecordHash();
            }
        }

        Instant now = Instant.now();
        String status = mismatches > 0 ? "FAILED" : "PASSED";
        VerificationRunEntity run = verificationRepository.save(VerificationRunEntity.builder()
                .id(UUID.randomUUID())
                .runId(support.idPrefix("ver_"))
                .scope(req.getScope())
                .chainId(req.getChainId())
                .claimId(req.getClaimId())
                .mode(req.getMode())
                .status(status)
                .checkedRecords(checked)
                .mismatchCount(mismatches)
                .initiatedBy(id.userId())
                .reason(req.getReason())
                .note(req.getNote())
                .startedAt(now)
                .completedAt(now)
                .build());

        if (mismatches > 0 && mismatchRecord != null) {
            keeperService.raiseFinding("HASH_CHAIN_MISMATCH", FindingSeverity.CRITICAL, mismatchRecord,
                    "Chain verification detected " + mismatches + " hash mismatch(es)");
            support.emitOutbox("audit.integrity.alert", run.getRunId(),
                    Map.of("runId", run.getRunId(), "mismatches", mismatches));
        }

        return Map.of("runId", run.getRunId(), "status", status, "checkedRecords", checked, "mismatchCount", mismatches);
    }

    @Transactional
    public ReportsListResponse listReports(String reportId, boolean download) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        if (!id.hasAnyRole("COMPLIANCE_OFFICER", "AUDITOR", "REGULATOR_VIEWER")) {
            throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient role");
        }
        boolean regulatorOnlyFinal = id.hasRole("REGULATOR_VIEWER") && !id.hasAnyRole("COMPLIANCE_OFFICER", "AUDITOR");

        if (download && reportId != null) {
            RegulatoryReportEntity rep = reportRepository.findByReportId(reportId)
                    .orElseThrow(() -> new AuditApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Report not found"));
            if (regulatorOnlyFinal && !"FINAL".equals(rep.getStatus())) {
                throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Regulators may only download FINAL reports");
            }
            support.appendAccessRecord(id.userId(), "DOWNLOAD_REPORT", reportId);
            return ReportsListResponse.builder()
                    .report(Map.of("reportId", rep.getReportId(), "status", rep.getStatus(), "reportHash", rep.getReportHash()))
                    .download(Map.of("token", support.idPrefix("dl_"), "expiresAt", Instant.now().plus(15, ChronoUnit.MINUTES).toString()))
                    .build();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (RegulatoryReportEntity r : reportRepository.findAll()) {
            if (regulatorOnlyFinal && !"FINAL".equals(r.getStatus())) continue;
            items.add(Map.of("reportId", r.getReportId(), "reportType", r.getReportType().name(),
                    "status", r.getStatus(), "reportHash", r.getReportHash()));
        }
        return ReportsListResponse.builder().items(items).build();
    }

    @Transactional(readOnly = true)
    public FindingsListResponse listFindings(String statusFilter) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        boolean countsOnly = id.hasRole("ADMIN") && !id.hasAnyRole("COMPLIANCE_OFFICER", "AUDITOR", "SECURITY_OFFICER");
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> counts = new LinkedHashMap<>();
        long open = 0, resolved = 0, accepted = 0;

        for (AuditFindingEntity f : findingRepository.findAll()) {
            switch (f.getStatus()) {
                case OPEN, UNDER_REVIEW -> open++;
                case RESOLVED, FALSE_POSITIVE -> resolved++;
                case ACCEPTED_RISK -> accepted++;
            }
            if (!countsOnly && (statusFilter == null || statusFilter.equalsIgnoreCase(f.getStatus().name()))) {
                items.add(Map.of("findingId", f.getFindingId(), "ruleCode", f.getRuleCode(),
                        "severity", f.getSeverity().name(), "status", f.getStatus().name()));
            }
        }
        counts.put("open", open);
        counts.put("resolved", resolved);
        counts.put("acceptedRisk", accepted);
        return FindingsListResponse.builder().items(countsOnly ? null : items).counts(counts).build();
    }

    @Transactional
    public FindingDispositionResponse disposeFinding(String findingId, FindingDispositionRequest req) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        AuditFindingEntity f = findingRepository.findByFindingId(findingId)
                .orElseThrow(() -> new AuditApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Finding not found"));

        boolean isIntegrity = "HASH_CHAIN_MISMATCH".equals(f.getRuleCode());
        if (isIntegrity && !id.hasAnyRole("SECURITY_OFFICER", "COMPLIANCE_OFFICER")) {
            throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Integrity finding requires SECURITY_OFFICER or COMPLIANCE_OFFICER");
        }
        if (!isIntegrity && !id.hasAnyRole("COMPLIANCE_OFFICER", "SECURITY_OFFICER")) {
            throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Requires COMPLIANCE_OFFICER");
        }
        Instant now = Instant.now();

        if (req.getApproveProposal() != null && !req.getApproveProposal().isBlank()) {
            if (!req.getApproveProposal().equals(f.getProposalHash())) {
                throw new AuditApiException(HttpStatus.CONFLICT, "PROPOSAL_CHANGED", "Proposal hash mismatch");
            }
            if (id.userId().equals(f.getProposedBy())) {
                throw new AuditApiException(HttpStatus.FORBIDDEN, "APPROVER_NOT_INDEPENDENT", "Proposer cannot approve own finding disposition");
            }
            FindingAction pending = FindingAction.valueOf(f.getPendingAction());
            f.setStatus(mapActionToStatus(pending));
            f.setApprovedBy(id.userId());
            f.setPendingAction(null);
            f.setUpdatedAt(now);
            f = findingRepository.save(f);
            return toFindingResponse(f, pending.name(), "APPROVED", List.of());
        }

        FindingAction action = req.getAction();
        if (action == null) {
            throw new AuditApiException(HttpStatus.BAD_REQUEST, "ACTION_REQUIRED", "Action is required");
        }
        if (action == FindingAction.RESOLVE || action == FindingAction.MARK_FALSE_POSITIVE || action == FindingAction.ACCEPT_RISK) {
            if (req.getEvidenceRefs() == null || req.getEvidenceRefs().isEmpty()) {
                throw new AuditApiException(HttpStatus.UNPROCESSABLE_CONTENT, "EVIDENCE_REQUIRED", "Evidence references are required");
            }
        }
        if (action == FindingAction.ACCEPT_RISK) {
            if (req.getAcceptUntil() == null || req.getAcceptUntil().isBefore(LocalDate.now())
                    || req.getAcceptUntil().isAfter(LocalDate.now().plusMonths(properties.getRules().getMaxAcceptRiskMonths()))) {
                throw new AuditApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ACCEPT_UNTIL_INVALID", "acceptUntil must be within 12 months");
            }
            f.setAcceptUntil(req.getAcceptUntil());
        }

        if (req.getEvidenceRefs() != null) {
            f.setEvidenceRefsJson(support.toJson(req.getEvidenceRefs()));
        }

        boolean dualControl = action == FindingAction.ACCEPT_RISK
                || f.getSeverity() == FindingSeverity.CRITICAL
                || (f.getSeverity() == FindingSeverity.HIGH && action == FindingAction.MARK_FALSE_POSITIVE);

        if (dualControl) {
            String proposalHash = AuditExternalClients.sha256(findingId + "|" + action.name() + "|" + id.userId());
            f.setPendingAction(action.name());
            f.setProposalHash(proposalHash);
            f.setProposedBy(id.userId());
            f.setUpdatedAt(now);
            f = findingRepository.save(f);
            return toFindingResponse(f, action.name(), "PENDING_APPROVAL", List.of("COMPLIANCE_OFFICER_SECOND"));
        }

        f.setStatus(mapActionToStatus(action));
        f.setUpdatedAt(now);
        f = findingRepository.save(f);
        return toFindingResponse(f, action.name(), "APPLIED", List.of());
    }

    private FindingStatus mapActionToStatus(FindingAction action) {
        return switch (action) {
            case RESOLVE -> FindingStatus.RESOLVED;
            case MARK_FALSE_POSITIVE -> FindingStatus.FALSE_POSITIVE;
            case ACCEPT_RISK -> FindingStatus.ACCEPTED_RISK;
            case REVIEW, ACKNOWLEDGE -> FindingStatus.UNDER_REVIEW;
            case ESCALATE, REOPEN -> FindingStatus.OPEN;
        };
    }

    private FindingDispositionResponse toFindingResponse(AuditFindingEntity f, String action, String approvalStatus, List<String> approvalsReq) {
        return FindingDispositionResponse.builder()
                .findingId(f.getFindingId())
                .status(f.getStatus().name())
                .action(action)
                .approvalStatus(approvalStatus)
                .approvalsRequired(approvalsReq)
                .proposalHash(f.getProposalHash())
                .acceptUntil(f.getAcceptUntil() != null ? f.getAcceptUntil().toString() : null)
                .history(support.fromJsonListMap(f.getHistoryJson()))
                .version(f.getVersion())
                .build();
    }

    @Transactional
    public LegalHoldResponse manageLegalHold(LegalHoldRequest req) {
        AuditIdentityFilter.Identity id = AuditIdentityFilter.current();
        if (!id.hasRole("COMPLIANCE_OFFICER")) {
            throw new AuditApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Requires COMPLIANCE_OFFICER");
        }
        Instant now = Instant.now();

        if (req.getAction() == HoldAction.PLACE) {
            String holdId = support.idPrefix("hld_");
            Map<String, Object> scope = req.getScope() != null ? req.getScope() : Map.of();
            LegalHoldEntity hold = holdRepository.save(LegalHoldEntity.builder()
                    .id(UUID.randomUUID())
                    .holdId(holdId)
                    .status("ACTIVE")
                    .caseRef(req.getCaseRef() != null ? req.getCaseRef() : "CASE_UNSPECIFIED")
                    .reasonCode(req.getReasonCode() != null ? req.getReasonCode() : "LEGAL_HOLD")
                    .scopeJson(support.toJson(scope))
                    .resolvedJson(support.toJson(Map.of("erasureBlocked", true)))
                    .placedBy(id.userId())
                    .note(req.getNote())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            clients.syncHoldToVault(holdId, scope, true);
            support.emitOutbox("audit.hold.changed", holdId, Map.of("holdId", holdId, "status", "ACTIVE"));
            return toHoldResponse(hold, List.of());
        }

        LegalHoldEntity hold = holdRepository.findByHoldId(req.getHoldId())
                .orElseThrow(() -> new AuditApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Legal hold not found"));
        if (req.getApproveProposal() != null && !req.getApproveProposal().isBlank()) {
            if (!req.getApproveProposal().equals(hold.getProposalHash())) {
                throw new AuditApiException(HttpStatus.CONFLICT, "PROPOSAL_CHANGED", "Proposal hash mismatch");
            }
            if (id.userId().equals(hold.getReleaseProposedBy())) {
                throw new AuditApiException(HttpStatus.FORBIDDEN, "APPROVER_NOT_INDEPENDENT", "Release proposer cannot self-approve");
            }
            hold.setReleaseApprovedBy(id.userId());
            hold.setStatus("RELEASED");
            hold.setUpdatedAt(now);
            hold = holdRepository.save(hold);
            clients.syncHoldToVault(hold.getHoldId(), support.fromJsonMap(hold.getScopeJson()), false);
            support.emitOutbox("audit.hold.changed", hold.getHoldId(), Map.of("holdId", hold.getHoldId(), "status", "RELEASED"));
            return toHoldResponse(hold, List.of());
        }

        String pHash = AuditExternalClients.sha256(hold.getHoldId() + "|RELEASE|" + id.userId());
        hold.setStatus("PENDING_RELEASE");
        hold.setReleaseProposedBy(id.userId());
        hold.setProposalHash(pHash);
        hold.setUpdatedAt(now);
        hold = holdRepository.save(hold);
        return toHoldResponse(hold, List.of("COMPLIANCE_OFFICER_SECOND"));
    }

    private LegalHoldResponse toHoldResponse(LegalHoldEntity h, List<String> approvalsReq) {
        return LegalHoldResponse.builder()
                .holdId(h.getHoldId())
                .status(h.getStatus())
                .caseRef(h.getCaseRef())
                .reasonCode(h.getReasonCode())
                .scope(support.fromJsonMap(h.getScopeJson()))
                .resolved(support.fromJsonMap(h.getResolvedJson()))
                .approvalsRequired(approvalsReq)
                .proposalHash(h.getProposalHash())
                .version(h.getVersion())
                .build();
    }

    private boolean verifyChainHashes(String chainId) {
        List<AuditRecordEntity> recs = recordRepository.findByChainIdOrderBySeqAsc(chainId);
        String expectedPrev = "GENESIS_" + chainId;
        for (AuditRecordEntity r : recs) {
            String recomputed = support.computeRecordHash(chainId, r.getSeq(), expectedPrev, r.getPayloadJson());
            if (!expectedPrev.equals(r.getPrevHash()) || !recomputed.equals(r.getRecordHash())) {
                return false;
            }
            expectedPrev = r.getRecordHash();
        }
        return true;
    }
}
