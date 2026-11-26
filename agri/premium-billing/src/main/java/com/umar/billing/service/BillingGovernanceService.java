package com.umar.billing.service;

import com.umar.billing.client.BillingExternalClients;
import com.umar.billing.config.BillingProperties;
import com.umar.billing.entity.*;
import com.umar.billing.exception.BillingApiException;
import com.umar.billing.repository.*;
import com.umar.billing.security.BillingIdentity;
import com.umar.billing.security.BillingSecurityContext;
import com.umar.enums.auth.AuthRole;
import com.umar.enums.billing.*;
import com.umar.enums.policy.PayerType;
import com.umar.payload.request.billing.*;
import com.umar.payload.response.billing.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BillingGovernanceService {

    private final InstalmentPlanRepository planRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository lineRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final BillingApprovalRepository approvalRepository;
    private final SponsorAccountRepository sponsorRepository;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationBreakRepository breakRepository;
    private final BillingCoreService billingCoreService;
    private final BillingExternalClients clients;
    private final BillingOutboxService outboxService;
    private final BillingIdempotencyService idempotencyService;
    private final BillingProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public InstalmentPlanResponse configureInstalmentPlan(ConfigureInstalmentPlanRequest req) {
        BillingIdentity actor = BillingSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        List<String> problems = new ArrayList<>();
        double pctSum = req.getInstalments().stream().mapToDouble(ConfigureInstalmentPlanRequest.InstalmentSpec::getPercent).sum();
        if (Math.abs(pctSum - 100.0) > 0.01) problems.add("PERCENTAGES_NOT_100");
        if (req.getDepositPercent() < properties.getRules().getMinDepositPercent()) problems.add("DEPOSIT_BELOW_MINIMUM");
        if (!Objects.equals(req.getDepositPercent(), req.getInstalments().getFirst().getPercent())) problems.add("DEPOSIT_MISMATCH_FIRST_INSTALMENT");
        if (req.getActivationThresholdPercent() < req.getDepositPercent() || req.getActivationThresholdPercent() > 100.0) {
            problems.add("ACTIVATION_BELOW_DEPOSIT");
        }
        for (int i = 1; i < req.getInstalments().size(); i++) {
            if (req.getInstalments().get(i).getDueOffsetDays() <= req.getInstalments().get(i - 1).getDueOffsetDays()) {
                problems.add("OFFSETS_NOT_RISING");
                break;
            }
        }
        if (!problems.isEmpty()) {
            throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLAN_INVALID",
                    "The plan is not valid", Map.of("problems", problems));
        }

        String scheduleJson = writeJson(req.getInstalments());
        String planHash = clients.sha256(req.getPlanCode() + "|" + scheduleJson + "|" + req.getDepositPercent()
                + "|" + req.getActivationThresholdPercent());

        Optional<InstalmentPlanEntity> existingSame = planRepository.findByPlanCodeAndPlanHash(req.getPlanCode(), planHash);
        if (existingSame.isPresent()) {
            InstalmentPlanEntity p = existingSame.get();
            return InstalmentPlanResponse.builder()
                    .planId(p.getPlanId()).planCode(p.getPlanCode()).version(p.getVersionNum())
                    .status(p.getStatus().name()).planHash(p.getPlanHash())
                    .effectiveFrom(p.getEffectiveFrom().toString()).build();
        }

        if (!planRepository.findByPlanCodeAndStatus(req.getPlanCode(), PlanStatus.PENDING_APPROVAL).isEmpty()) {
            throw new BillingApiException(HttpStatus.CONFLICT, "PLAN_DRAFT_EXISTS",
                    "Finish or withdraw the open draft first");
        }

        int versionNum = 1;
        if (req.getBasePlanId() != null && !req.getBasePlanId().isBlank()) {
            InstalmentPlanEntity base = planRepository.findById(req.getBasePlanId())
                    .orElseThrow(() -> new BillingApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan not found"));
            versionNum = base.getVersionNum() + 1;
        } else {
            versionNum = planRepository.findFirstByPlanCodeOrderByVersionNumDesc(req.getPlanCode())
                    .map(p -> p.getVersionNum() + 1).orElse(1);
        }

        Instant now = Instant.now();
        String planId = "ipl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        InstalmentPlanEntity plan = planRepository.save(InstalmentPlanEntity.builder()
                .planId(planId)
                .planCode(req.getPlanCode())
                .versionNum(versionNum)
                .nameJson(writeJson(req.getName()))
                .scopeJson(writeJson(req.getScope()))
                .scheduleJson(scheduleJson)
                .depositPercent(req.getDepositPercent())
                .activationThresholdPercent(req.getActivationThresholdPercent())
                .minInstalmentMinor(req.getMinInstalmentMinor())
                .graceJson(writeJson(req.getGrace()))
                .lapseJson(writeJson(req.getLapseRule()))
                .reinstatementJson(writeJson(req.getReinstatement()))
                .remindersJson(writeJson(req.getReminders()))
                .status(PlanStatus.PENDING_APPROVAL)
                .planHash(planHash)
                .diffJson("[]")
                .authorId(actor.actorId())
                .effectiveFrom(req.getEffectiveFrom())
                .createdAt(now)
                .build());

        String aprId = "apr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        approvalRepository.save(BillingApprovalEntity.builder()
                .approvalId(aprId)
                .type(ApprovalType.INSTALMENT_PLAN)
                .subjectId(planId)
                .subjectHash(planHash)
                .requestedBy(actor.actorId())
                .requiredLevels(2)
                .requiredRolesCsv("FINANCE,COMPLIANCE_OFFICER")
                .decisionsJson("[]")
                .status(ApprovalStatus.OPEN)
                .createdAt(now)
                .build());

        return InstalmentPlanResponse.builder()
                .planId(planId)
                .planCode(req.getPlanCode())
                .version(versionNum)
                .status(PlanStatus.PENDING_APPROVAL.name())
                .planHash(planHash)
                .diffSummary(List.of())
                .approvalId(aprId)
                .approvalsRequired(List.of("FINANCE", "COMPLIANCE_OFFICER"))
                .effectiveFrom(req.getEffectiveFrom().toString())
                .build();
    }

    @Transactional
    public RefundResponse requestRefund(RequestRefundRequest req, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        BillingIdentity actor = BillingSecurityContext.require();
        boolean isSystem = actor.isServiceCaller();
        if (!isSystem && !actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        Optional<RefundResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "BIL-06", req, RefundResponse.class);
        if (replay.isPresent()) return replay.get();

        if (req.getSourceEventId() != null && !req.getSourceEventId().isBlank()) {
            Optional<RefundEntity> existing = refundRepository.findBySourceEventId(req.getSourceEventId());
            if (existing.isPresent()) {
                throw new BillingApiException(HttpStatus.CONFLICT, "REFUND_EXISTS",
                        "A refund already exists", Map.of("refundId", existing.get().getRefundId()));
            }
        }

        InvoiceEntity invoice = invoiceRepository.findById(req.getInvoiceId())
                .orElseThrow(() -> new BillingApiException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Invoice not found"));

        List<PaymentEntity> completed = paymentRepository.findByInvoiceIdAndStatusOrderByReceivedAtDesc(
                invoice.getInvoiceId(), PaymentStatus.COMPLETED);
        if (completed.isEmpty()) {
            throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_REFUNDABLE_PAYMENT",
                    "There is nothing to refund");
        }
        long priorRefunds = refundRepository.findByInvoiceIdOrderByCreatedAtDesc(invoice.getInvoiceId()).stream()
                .filter(r -> r.getStatus() != RefundStatus.REJECTED && r.getStatus() != RefundStatus.FAILED)
                .mapToLong(RefundEntity::getAmountMinor).sum();
        long nonRefundableFees = lineRepository.findByInvoiceId(invoice.getInvoiceId()).stream()
                .filter(l -> l.getCode() == InvoiceLineCode.FEE && !l.isRefundable())
                .mapToLong(InvoiceLineEntity::getAmountMinor).sum();
        long refundableMinor = Math.max(0L, completed.stream().mapToLong(PaymentEntity::getAmountMinor).sum()
                - priorRefunds - nonRefundableFees);
        if (req.getAmountMinor() > refundableMinor) {
            throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REFUND_EXCEEDS_PAID",
                    "The refund is more than was paid", Map.of("refundableMinor", refundableMinor));
        }

        PaymentEntity orig = completed.getFirst();
        String destType = orig.getMethod().name();
        String destToken = orig.getPayerToken();
        String maskedDest = Objects.toString(orig.getMaskedPayer(), "SPONSOR");
        if (invoice.getPayerType() == PayerType.SPONSOR || invoice.getPayerType() == PayerType.LENDER) {
            SponsorAccountEntity sp = sponsorRepository.findById(invoice.getPayerRef()).orElse(null);
            destType = "SPONSOR";
            destToken = sp != null ? sp.getBankToken() : null;
            maskedDest = sp != null ? sp.getMaskedBank() : "SPONSOR_BANK";
        }

        String refundId = "rf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Instant now = Instant.now();
        boolean hookHeld = clients.isPreRefundHold();
        boolean aboveLimit = req.getAmountMinor() > properties.getRules().getRefundSingleLimitMinor();
        int levels = hookHeld ? 1 : (aboveLimit ? 2 : (isSystem ? 0 : 1));
        String aprId = null;
        RefundStatus status;

        if (levels > 0) {
            status = hookHeld ? RefundStatus.HELD : RefundStatus.PENDING_APPROVAL;
            aprId = "apr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String rolesCsv = levels == 2 ? "FINANCE,FINANCE" : "FINANCE";
            String hash = clients.sha256(refundId + "|" + req.getAmountMinor() + "|" + maskedDest);
            approvalRepository.save(BillingApprovalEntity.builder()
                    .approvalId(aprId)
                    .type(ApprovalType.REFUND)
                    .subjectId(refundId)
                    .subjectHash(hash)
                    .requestedBy(actor.actorId())
                    .requiredLevels(levels)
                    .requiredRolesCsv(rolesCsv)
                    .decisionsJson("[]")
                    .status(ApprovalStatus.OPEN)
                    .createdAt(now)
                    .build());
            outboxService.enqueue(properties.getKafka().getBillingTopic(), "refund.approval.required", refundId, Map.of(
                    "eventType", "refund.approval.required",
                    "refundId", refundId, "invoiceId", invoice.getInvoiceId(),
                    "amountMinor", req.getAmountMinor(), "levels", levels,
                    "reasonCode", req.getReasonCode().name(), "timestamp", now.toString()));
        } else {
            status = RefundStatus.PROCESSING;
        }

        String provRef = status == RefundStatus.PROCESSING
                ? clients.sendRefundDisbursement(orig.getProvider(), req.getAmountMinor(), destToken) : null;

        refundRepository.save(RefundEntity.builder()
                .refundId(refundId)
                .invoiceId(invoice.getInvoiceId())
                .policyId(invoice.getPolicyId())
                .paymentIdsCsv(orig.getPaymentId())
                .amountMinor(req.getAmountMinor())
                .currency(invoice.getCurrency())
                .reasonCode(req.getReasonCode())
                .sourceEventId(req.getSourceEventId())
                .status(status)
                .destinationType(destType)
                .destinationToken(destToken)
                .maskedDestination(maskedDest)
                .provider(orig.getProvider())
                .providerRef(provRef)
                .approvalId(aprId)
                .requestedBy(actor.actorId())
                .createdAt(now)
                .build());

        RefundResponse resp = RefundResponse.builder()
                .refundId(refundId)
                .invoiceId(invoice.getInvoiceId())
                .policyId(invoice.getPolicyId())
                .status(status.name())
                .amountMinor(req.getAmountMinor())
                .currency(invoice.getCurrency())
                .reasonCode(req.getReasonCode().name())
                .destination(Map.of("type", destType, "maskedReference", maskedDest))
                .approvalRequired(aprId != null ? Map.of("levels", levels, "approvalId", aprId) : Map.of("levels", 0))
                .createdAt(now.toString())
                .build();
        idempotencyService.save(idempotencyKey, "BIL-06", req, 202, resp);
        return resp;
    }

    @Transactional
    public BillingApprovalResponse decideApproval(String approvalId, BillingApprovalDecisionRequest req) {
        BillingIdentity actor = BillingSecurityContext.require();
        BillingApprovalEntity apr = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new BillingApiException(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "Approval not found"));

        if (apr.getStatus() != ApprovalStatus.OPEN) {
            throw new BillingApiException(HttpStatus.CONFLICT, "APPROVAL_NOT_OPEN", "This approval is no longer open");
        }
        if (actor.actorId().equals(apr.getRequestedBy())) {
            throw new BillingApiException(HttpStatus.FORBIDDEN, "REQUESTER_CANNOT_APPROVE",
                    "You cannot approve your own request");
        }
        if (!req.getSubjectHash().equals(apr.getSubjectHash())) {
            throw new BillingApiException(HttpStatus.CONFLICT, "SUBJECT_CHANGED",
                    "The request changed. Please review again");
        }

        List<String> requiredRoles = Arrays.asList(apr.getRequiredRolesCsv().split(","));
        AuthRole actingRole;
        if (apr.getType() == ApprovalType.INSTALMENT_PLAN && actor.hasRole(AuthRole.COMPLIANCE_OFFICER)) {
            actingRole = AuthRole.COMPLIANCE_OFFICER;
        } else if (actor.hasRole(AuthRole.FINANCE) || actor.hasRole(AuthRole.ADMIN)) {
            actingRole = AuthRole.FINANCE;
        } else {
            throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (!requiredRoles.contains(actingRole.name()) && !actor.hasRole(AuthRole.ADMIN)) {
            throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Role cannot decide this approval");
        }

        List<Map<String, Object>> decisions = new ArrayList<>(readList(apr.getDecisionsJson()));
        if (decisions.stream().anyMatch(d -> Objects.equals(d.get("approver"), actor.actorId()))) {
            throw new BillingApiException(HttpStatus.CONFLICT, "ALREADY_DECIDED_BY_YOU",
                    "You have already decided this approval");
        }

        if (req.getDecision() == ApprovalDecisionType.APPROVE) {
            if (req.getChecklist() == null || req.getChecklist().values().stream().anyMatch(v -> !Boolean.TRUE.equals(v))
                    || req.getChecklist().isEmpty()) {
                throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Complete checklist required");
            }
        }

        Instant now = Instant.now();
        decisions.add(Map.of(
                "role", actingRole.name(),
                "approver", actor.actorId(),
                "decision", req.getDecision().name(),
                "at", now.toString()));
        apr.setDecisionsJson(writeJson(decisions));

        String subjectStatus;
        List<String> remaining = computeRemainingRoles(requiredRoles, decisions);

        if (req.getDecision() == ApprovalDecisionType.REJECT) {
            apr.setStatus(ApprovalStatus.REJECTED);
            subjectStatus = releaseSubjectOnRejection(apr);
            remaining = List.of();
        } else if (decisions.size() >= apr.getRequiredLevels() && remaining.isEmpty()) {
            apr.setStatus(ApprovalStatus.APPROVED);
            subjectStatus = releaseSubjectOnApproval(apr, actor.actorId(), now);
        } else {
            subjectStatus = "PENDING_APPROVAL";
        }
        approvalRepository.save(apr);

        return BillingApprovalResponse.builder()
                .approvalId(apr.getApprovalId())
                .type(apr.getType().name())
                .subjectId(apr.getSubjectId())
                .status(apr.getStatus().name())
                .decisions(decisions)
                .remaining(remaining)
                .subjectStatus(subjectStatus)
                .subjectHash(apr.getSubjectHash())
                .build();
    }

    @Transactional
    public ReconciliationRunResponse importStatement(ImportStatementRequest req) {
        BillingIdentity actor = BillingSecurityContext.require();
        if (!actor.isServiceCaller() && !actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (req.getChecksum() != null && !req.getChecksum().isBlank()) {
            Optional<ReconciliationRunEntity> existing = runRepository.findBySourceAndStatementDateAndChecksum(
                    req.getSource(), req.getStatementDate(), req.getChecksum());
            if (existing.isPresent()) {
                throw new BillingApiException(HttpStatus.CONFLICT, "STATEMENT_ALREADY_IMPORTED",
                        "This statement was already imported", Map.of("runId", existing.get().getRunId()));
            }
        }

        List<BillingExternalClients.StatementLine> lines;
        try {
            lines = clients.parseStatement(Objects.toString(req.getFileToken(), ""), req.getChecksum());
        } catch (IllegalArgumentException e) {
            throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "STATEMENT_INVALID",
                    "The statement could not be read", Map.of("detail", e.getMessage()));
        }

        String runId = "rec_" + req.getStatementDate().toString().replace("-", "") + "_" + req.getSource().toLowerCase();
        Instant now = Instant.now();
        int matched = 0, unmatched = 0, appliedCount = 0, heldCount = 0, highSev = 0;
        long appliedTotal = 0, heldTotal = 0, feeTotal = 0;
        Map<String, Integer> breakCounts = new LinkedHashMap<>();

        Set<String> seenTxnIds = new HashSet<>();
        for (BillingExternalClients.StatementLine sl : lines) {
            if ("FEE".equalsIgnoreCase(sl.type())) {
                feeTotal += sl.amountMinor();
                billingCoreService.postJournal("PROVIDER_FEE", sl.amountMinor(), 0, sl.currency(), runId, now);
                matched++;
                continue;
            }
            seenTxnIds.add(sl.providerTxnId());
            Optional<PaymentEntity> payOpt = paymentRepository.findByProviderAndProviderTxnId(
                    req.getSource().toLowerCase(), sl.providerTxnId());
            if (payOpt.isPresent()) {
                PaymentEntity p = payOpt.get();
                if (Objects.equals(p.getAmountMinor(), sl.amountMinor())) {
                    matched++;
                } else {
                    unmatched++;
                    breakCounts.merge(BreakType.AMOUNT_MISMATCH.name(), 1, Integer::sum);
                    saveBreak(runId, BreakType.AMOUNT_MISMATCH, BreakSeverity.MEDIUM, "OPEN",
                            sl.providerTxnId(), p.getPaymentId(), sl.amountMinor(), "Statement amount differs", now);
                }
            } else if (Boolean.TRUE.equals(req.getAutoApply()) && sl.paymentRef() != null) {
                Optional<InvoiceEntity> invOpt = invoiceRepository.findByPaymentReference(sl.paymentRef());
                if (invOpt.isPresent() && invOpt.get().getOutstandingMinor() >= sl.amountMinor()) {
                    billingCoreService.applyPaymentToInvoice(invOpt.get(), null, PaymentMethod.MOBILE_MONEY,
                            PaymentSource.ONLINE, sl.amountMinor(), null, "Statement match", null, null,
                            req.getSource().toLowerCase(), sl.providerTxnId(), "reconciliation", now);
                    appliedCount++;
                    appliedTotal += sl.amountMinor();
                    unmatched++;
                    breakCounts.merge(BreakType.MISSING_IN_PLATFORM.name(), 1, Integer::sum);
                    saveBreak(runId, BreakType.MISSING_IN_PLATFORM, BreakSeverity.LOW, "RESOLVED_AUTO",
                            sl.providerTxnId(), null, sl.amountMinor(), "Applied from statement", now);
                } else {
                    unmatched++;
                    breakCounts.merge(BreakType.UNMATCHED.name(), 1, Integer::sum);
                    billingCoreService.postJournal("SUSPENSE", 0, sl.amountMinor(), sl.currency(), runId, now);
                    saveBreak(runId, BreakType.UNMATCHED, BreakSeverity.MEDIUM, "SUSPENSE",
                            sl.providerTxnId(), null, sl.amountMinor(), "Parked in suspense", now);
                }
            }
        }

        // Check platform payments missing in statement for that provider
        for (PaymentEntity p : paymentRepository.findAll()) {
            if (req.getSource().equalsIgnoreCase(p.getProvider())
                    && p.getStatus() == PaymentStatus.COMPLETED
                    && p.getProviderTxnId() != null
                    && !seenTxnIds.contains(p.getProviderTxnId())) {
                p.setStatus(PaymentStatus.HELD);
                paymentRepository.save(p);
                heldCount++;
                heldTotal += p.getAmountMinor();
                highSev++;
                unmatched++;
                breakCounts.merge(BreakType.MISSING_IN_PROVIDER.name(), 1, Integer::sum);
                saveBreak(runId, BreakType.MISSING_IN_PROVIDER, BreakSeverity.HIGH, "OPEN",
                        p.getProviderTxnId(), p.getPaymentId(), p.getAmountMinor(), "Not present on provider statement", now);
            }
        }

        Map<String, Object> breaksSummary = new LinkedHashMap<>(breakCounts);
        breaksSummary.put("highSeverity", highSev);
        Map<String, Object> summary = Map.of(
                "lines", Map.of("total", lines.size(), "matched", matched, "unmatched", unmatched),
                "breaks", breaksSummary,
                "applied", Map.of("count", appliedCount, "totalMinor", appliedTotal),
                "held", Map.of("count", heldCount, "totalMinor", heldTotal),
                "fees", Map.of("totalMinor", feeTotal));

        runRepository.save(ReconciliationRunEntity.builder()
                .runId(runId)
                .source(req.getSource())
                .statementDate(req.getStatementDate())
                .checksum(req.getChecksum())
                .totalLines(lines.size())
                .matchedLines(matched)
                .unmatchedLines(unmatched)
                .summaryJson(writeJson(summary))
                .completedAt(now)
                .build());

        outboxService.enqueue(properties.getKafka().getBillingTopic(), "reconciliation.completed", runId, Map.of(
                "eventType", "reconciliation.completed",
                "runId", runId, "source", req.getSource(),
                "statementDate", req.getStatementDate().toString(),
                "matchedCount", matched, "breakCount", unmatched,
                "highSeverityCount", highSev, "timestamp", now.toString()));

        return ReconciliationRunResponse.builder()
                .runId(runId)
                .source(req.getSource())
                .statementDate(req.getStatementDate().toString())
                .lines(Map.of("total", lines.size(), "matched", matched, "unmatched", unmatched))
                .breaks(breaksSummary)
                .applied(Map.of("count", appliedCount, "totalMinor", appliedTotal))
                .held(Map.of("count", heldCount, "totalMinor", heldTotal))
                .fees(Map.of("totalMinor", feeTotal))
                .completedAt(now.toString())
                .build();
    }

    private void saveBreak(String runId, BreakType type, BreakSeverity sev, String status,
                           String provTxnId, String payId, long amt, String detail, Instant now) {
        String brkId = "brk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        breakRepository.save(ReconciliationBreakEntity.builder()
                .breakId(brkId)
                .runId(runId)
                .type(type)
                .severity(sev)
                .status(status)
                .providerTxnId(provTxnId)
                .paymentId(payId)
                .amountMinor(amt)
                .detail(detail)
                .createdAt(now)
                .build());
        outboxService.enqueue(properties.getKafka().getBillingTopic(), "reconciliation.break.detected", brkId, Map.of(
                "eventType", "reconciliation.break.detected",
                "breakId", brkId, "runId", runId, "type", type.name(),
                "severity", sev.name(), "amountMinor", amt,
                "providerTxnId", Objects.toString(provTxnId, ""), "timestamp", now.toString()));
    }

    private List<String> computeRemainingRoles(List<String> required, List<Map<String, Object>> decisions) {
        List<String> rem = new ArrayList<>(required);
        for (Map<String, Object> d : decisions) {
            rem.remove(String.valueOf(d.get("role")));
        }
        return rem;
    }

    private String releaseSubjectOnApproval(BillingApprovalEntity apr, String actorId, Instant now) {
        return switch (apr.getType()) {
            case REFUND -> {
                RefundEntity rf = refundRepository.findById(apr.getSubjectId()).orElseThrow();
                rf.setStatus(RefundStatus.PROCESSING);
                rf.setProviderRef(clients.sendRefundDisbursement(rf.getProvider(), rf.getAmountMinor(), rf.getDestinationToken()));
                refundRepository.save(rf);
                yield RefundStatus.PROCESSING.name();
            }
            case INSTALMENT_PLAN -> {
                InstalmentPlanEntity plan = planRepository.findById(apr.getSubjectId()).orElseThrow();
                plan.setStatus(PlanStatus.APPROVED);
                planRepository.save(plan);
                yield PlanStatus.APPROVED.name();
            }
            case OFFLINE_PAYMENT -> {
                PaymentEntity p = paymentRepository.findById(apr.getSubjectId()).orElseThrow();
                InvoiceEntity inv = invoiceRepository.findById(p.getInvoiceId()).orElseThrow();
                p.setStatus(PaymentStatus.COMPLETED);
                paymentRepository.save(p);
                inv.setPaidMinor(inv.getPaidMinor() + p.getAmountMinor());
                inv.setOutstandingMinor(Math.max(0L, inv.getTotalMinor() - inv.getPaidMinor()));
                inv.setStatus(inv.getOutstandingMinor() == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID);
                invoiceRepository.save(inv);
                yield PaymentStatus.COMPLETED.name();
            }
        };
    }

    private String releaseSubjectOnRejection(BillingApprovalEntity apr) {
        return switch (apr.getType()) {
            case REFUND -> {
                RefundEntity rf = refundRepository.findById(apr.getSubjectId()).orElseThrow();
                rf.setStatus(RefundStatus.REJECTED);
                refundRepository.save(rf);
                yield RefundStatus.REJECTED.name();
            }
            case INSTALMENT_PLAN -> {
                InstalmentPlanEntity plan = planRepository.findById(apr.getSubjectId()).orElseThrow();
                plan.setStatus(PlanStatus.REJECTED);
                planRepository.save(plan);
                yield PlanStatus.REJECTED.name();
            }
            case OFFLINE_PAYMENT -> {
                PaymentEntity p = paymentRepository.findById(apr.getSubjectId()).orElseThrow();
                p.setStatus(PaymentStatus.REVERSED);
                paymentRepository.save(p);
                yield PaymentStatus.REVERSED.name();
            }
        };
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "[]"; }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String json) {
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return List.of(); }
    }
}
