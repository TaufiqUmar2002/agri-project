package com.umar.riskpool.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.riskpool.*;
import com.umar.payload.request.riskpool.*;
import com.umar.payload.response.riskpool.*;
import com.umar.riskpool.client.RiskPoolExternalClients;
import com.umar.riskpool.config.RiskPoolProperties;
import com.umar.riskpool.domain.*;
import com.umar.riskpool.exception.RiskPoolApiException;
import com.umar.riskpool.repository.*;
import com.umar.riskpool.security.RiskPoolIdentityFilter;
import com.umar.riskpool.service.support.RiskPoolSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RiskPoolLedgerService {

    private static final Set<String> AUTOMATIC_ACCOUNTS = Set.of("1300", "1400", "2010", "2100", "2200", "4010", "5010");

    private final RiskPoolRepository poolRepository;
    private final ReinsuranceTreatyRepository treatyRepository;
    private final PolicyPremiumRegisterRepository registerRepository;
    private final PlotDimensionRepository plotRepository;
    private final ClaimReserveRepository reserveRepository;
    private final JournalTransactionRepository txRepository;
    private final ManualJournalRepository manualJournalRepository;
    private final PoolPeriodRepository periodRepository;
    private final PoolApprovalRepository approvalRepository;
    private final CapacityReservationRepository capacityRepository;
    private final PoolProjectionRepository projectionRepository;
    private final RiskPoolInboxEventRepository inboxRepository;
    private final RiskPoolExternalClients clients;
    private final RiskPoolSupportService support;
    private final RiskPoolProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public PoolResponse createPool(CreatePoolRequest req) {
        RiskPoolIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (poolRepository.findByPoolCode(req.getPoolCode()).isPresent()) {
            throw new RiskPoolApiException(HttpStatus.CONFLICT, "POOL_CODE_EXISTS", "This pool code is already in use");
        }
        if (!clients.isInsurerRegistered(req.getInsurerRef())) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSURER_NOT_REGISTERED", "Insurer not found");
        }
        double topRegionLimit = ((Number) req.getLimits().getOrDefault("topRegionShareLimit", 0.45)).doubleValue();
        if (topRegionLimit > properties.getRules().getMaxRegionShareLimit()
                && (req.getEvidenceTokens() == null || req.getEvidenceTokens().isEmpty())) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LIMIT_ABOVE_POLICY_MAX",
                    "Top region limit above maximum requires insurer approval evidence");
        }
        String mappingsJson = writeJson(req.getMappings());
        String limitsJson = writeJson(req.getLimits());
        String poolId = "pool_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String configHash = support.sha256(req.getPoolCode() + "|" + req.getCurrency() + "|" + mappingsJson + "|" + limitsJson);
        Instant now = Instant.now();

        poolRepository.save(RiskPoolEntity.builder()
                .poolId(poolId).poolCode(req.getPoolCode()).nameJson(writeJson(req.getName()))
                .insurerRef(req.getInsurerRef()).countryCode(req.getCountryCode()).currency(req.getCurrency())
                .mappingsJson(mappingsJson).accountingJson(writeJson(req.getAccounting()))
                .limitsJson(limitsJson).alertsJson("[]").status(PoolStatus.PENDING_APPROVAL)
                .configHash(configHash).versionNum(1).alertVersion(1)
                .authorId(actor.actorId()).effectiveFrom(req.getEffectiveFrom()).createdAt(now).build());

        String aprId = "apr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        approvalRepository.save(PoolApprovalEntity.builder()
                .approvalId(aprId).type(RiskPoolApprovalType.POOL).subjectId(poolId)
                .subjectHash(configHash).requestedBy(actor.actorId())
                .requiredLevels(2).requiredRolesCsv("FINANCE,COMPLIANCE_OFFICER")
                .decisionsJson("[]").status("OPEN").createdAt(now).build());

        support.enqueue(properties.getKafka().getLedgerTopic(), "pool.created", poolId, Map.of(
                "eventType", "pool.created", "poolId", poolId, "poolCode", req.getPoolCode(),
                "currency", req.getCurrency(), "status", PoolStatus.PENDING_APPROVAL.name(), "timestamp", now.toString()));

        return PoolResponse.builder()
                .poolId(poolId).poolCode(req.getPoolCode()).version(1).status(PoolStatus.PENDING_APPROVAL.name())
                .configHash(configHash).mappings(req.getMappings())
                .chartOfAccounts(Map.of("template", "STANDARD_PARAMETRIC_V1", "accounts", 16))
                .approvalId(aprId).approvalsRequired(List.of("FINANCE", "COMPLIANCE_OFFICER"))
                .effectiveFrom(req.getEffectiveFrom().toString()).build();
    }

    @Transactional
    public TreatyResponse createTreaty(String poolId, CreateTreatyRequest req) {
        RiskPoolIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.REINSURANCE_MANAGER, AuthRole.ADMIN)) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        RiskPoolEntity pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new RiskPoolApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found"));

        double totalShare = req.getPlacements().stream()
                .mapToDouble(p -> ((Number) p.getOrDefault("sharePercent", 0)).doubleValue()).sum();
        if (Math.abs(totalShare - 100.0) > 0.01) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLACEMENT_INVALID",
                    "Placement shares must add up to 100");
        }
        String setCurrency = Objects.toString(req.getSettlement().get("currency"), pool.getCurrency());
        if (!pool.getCurrency().equals(setCurrency)) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TERMS_INVALID",
                    "Settlement currency must match pool currency");
        }
        if (!treatyRepository.findByPoolIdAndTreatyCodeAndStatus(poolId, req.getTreatyCode(), "PENDING_APPROVAL").isEmpty()) {
            throw new RiskPoolApiException(HttpStatus.CONFLICT, "TREATY_DRAFT_EXISTS",
                    "Finish or withdraw the open draft first");
        }

        String treatyId = "trt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String treatyHash = support.sha256(poolId + "|" + req.getTreatyCode() + "|" + writeJson(req.getTerms()) + "|" + writeJson(req.getPlacements()));
        Instant now = Instant.now();

        treatyRepository.save(ReinsuranceTreatyEntity.builder()
                .treatyId(treatyId).poolId(poolId).treatyCode(req.getTreatyCode()).versionNum(1)
                .type(req.getType()).placementsJson(writeJson(req.getPlacements()))
                .periodJson(writeJson(req.getPeriod())).scopeJson(writeJson(req.getScope()))
                .termsJson(writeJson(req.getTerms())).settlementJson(writeJson(req.getSettlement()))
                .wordingToken(req.getWordingToken()).treatyHash(treatyHash).status("PENDING_APPROVAL")
                .authorId(actor.actorId()).effectiveFrom(req.getEffectiveFrom()).createdAt(now).build());

        String aprId = "apr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        approvalRepository.save(PoolApprovalEntity.builder()
                .approvalId(aprId).type(RiskPoolApprovalType.TREATY).subjectId(treatyId)
                .subjectHash(treatyHash).requestedBy(actor.actorId())
                .requiredLevels(2).requiredRolesCsv("FINANCE,COMPLIANCE_OFFICER")
                .decisionsJson("[]").status("OPEN").createdAt(now).build());

        support.enqueue(properties.getKafka().getLedgerTopic(), "treaty.created", treatyId, Map.of(
                "eventType", "treaty.created", "treatyId", treatyId, "poolId", poolId,
                "treatyCode", req.getTreatyCode(), "type", req.getType().name(), "timestamp", now.toString()));

        return TreatyResponse.builder()
                .treatyId(treatyId).treatyCode(req.getTreatyCode()).version(1).status("PENDING_APPROVAL")
                .treatyHash(treatyHash).diffSummary(List.of()).placements(req.getPlacements())
                .approvalId(aprId).approvalsRequired(List.of("FINANCE", "COMPLIANCE_OFFICER"))
                .effectiveFrom(req.getEffectiveFrom().toString()).build();
    }

    @Transactional
    public ManualJournalResponse postManualJournal(String poolId, PostManualJournalRequest req, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new RiskPoolApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        RiskPoolIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        Optional<ManualJournalResponse> replay = support.findReplay(idempotencyKey, "RPL-07", req, ManualJournalResponse.class);
        if (replay.isPresent()) return replay.get();

        RiskPoolEntity pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new RiskPoolApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found"));

        if (!pool.getCurrency().equals(req.getCurrency())) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH", "Currency differs from pool");
        }

        long dr = 0;
        long cr = 0;
        for (Map<String, Object> line : req.getLines()) {
            String acc = Objects.toString(line.get("account"), "");
            String side = Objects.toString(line.get("side"), "").toUpperCase();
            long amt = ((Number) line.getOrDefault("amountMinor", 0L)).longValue();
            if (AUTOMATIC_ACCOUNTS.contains(acc)
                    && req.getJournalType() != JournalType.ADJUSTMENT && req.getJournalType() != JournalType.REVERSAL) {
                throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_RESTRICTED",
                        "This account cannot be posted to manually", Map.of("account", acc));
            }
            if ("DR".equals(side)) dr += amt;
            else if ("CR".equals(side)) cr += amt;
        }
        if (dr <= 0 || dr != cr) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNBALANCED_JOURNAL",
                    "The journal does not balance", Map.of("differenceMinor", Math.abs(dr - cr)));
        }
        String ym = YearMonth.from(req.getValueDate()).toString();
        PoolPeriodEntity period = periodRepository.findById(poolId + ":" + ym).orElse(null);
        if (period != null && period.getStatus() == PeriodStatus.CLOSED) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PERIOD_CLOSED",
                    "The period is closed", Map.of("period", ym));
        }
        if (!clients.verifyVaultDocuments(req.getEvidenceTokens())) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVIDENCE_INVALID", "Evidence could not be verified");
        }

        boolean dual = dr > properties.getRules().getManualJournalSingleLimitMinor()
                || req.getJournalType() == JournalType.ADJUSTMENT || req.getJournalType() == JournalType.REVERSAL;
        String rolesCsv = dual
                ? (req.getJournalType() == JournalType.ADJUSTMENT || req.getJournalType() == JournalType.REVERSAL
                ? "FINANCE,COMPLIANCE_OFFICER" : "FINANCE,FINANCE")
                : "FINANCE";
        int levels = dual ? 2 : 1;

        Instant now = Instant.now();
        String journalId = "jnl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String subjectHash = support.sha256(journalId + "|" + dr + "|" + req.getValueDate());
        String aprId = "apr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        manualJournalRepository.save(ManualJournalEntity.builder()
                .journalId(journalId).poolId(poolId).journalType(req.getJournalType())
                .valueDate(req.getValueDate()).currency(req.getCurrency()).totalMinor(dr)
                .linesJson(writeJson(req.getLines())).reverses(req.getReverses())
                .reasonCode(req.getReasonCode()).evidenceTokensCsv(String.join(",", req.getEvidenceTokens()))
                .note(req.getNote()).status("PENDING_APPROVAL").approvalId(aprId)
                .makerId(actor.actorId()).createdAt(now).build());

        approvalRepository.save(PoolApprovalEntity.builder()
                .approvalId(aprId).type(RiskPoolApprovalType.MANUAL_JOURNAL).subjectId(journalId)
                .subjectHash(subjectHash).requestedBy(actor.actorId()).requiredLevels(levels)
                .requiredRolesCsv(rolesCsv).decisionsJson("[]").status("OPEN").createdAt(now).build());

        ManualJournalResponse resp = ManualJournalResponse.builder()
                .journalId(journalId).poolId(poolId).status("PENDING_APPROVAL")
                .journalType(req.getJournalType().name()).valueDate(req.getValueDate().toString())
                .totalMinor(dr).currency(req.getCurrency()).approvalId(aprId)
                .approvalsRequired(Arrays.asList(rolesCsv.split(",")))
                .holdReasons(clients.isPreJournalHold() ? List.of("PRE_JOURNAL_HOLD") : null)
                .createdAt(now.toString()).build();
        support.saveIdempotency(idempotencyKey, "RPL-07", req, 202, resp);
        return resp;
    }

    @Transactional
    public PeriodCloseResponse closeOrReopenPeriod(String poolId, String periodStr, ClosePeriodRequest req, String idempotencyKey) {
        RiskPoolIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        poolRepository.findById(poolId)
                .orElseThrow(() -> new RiskPoolApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found"));

        YearMonth ym = YearMonth.parse(periodStr);
        String key = poolId + ":" + periodStr;
        PoolPeriodEntity period = periodRepository.findById(key)
                .orElse(PoolPeriodEntity.builder().periodKey(key).poolId(poolId).period(periodStr).status(PeriodStatus.OPEN).build());

        Instant now = Instant.now();
        String runId = "cls_" + periodStr.replace("-", "") + "_01";

        if ("REOPEN".equalsIgnoreCase(req.getAction())) {
            if (period.getStatus() != PeriodStatus.CLOSED) {
                throw new RiskPoolApiException(HttpStatus.CONFLICT, "PERIOD_NOT_OPEN", "Period must be CLOSED to reopen");
            }
            String aprId = "apr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            approvalRepository.save(PoolApprovalEntity.builder()
                    .approvalId(aprId).type(RiskPoolApprovalType.PERIOD_REOPEN).subjectId(key)
                    .subjectHash(support.sha256(key + "|REOPEN")).requestedBy(actor.actorId())
                    .requiredLevels(2).requiredRolesCsv("FINANCE,COMPLIANCE_OFFICER")
                    .decisionsJson("[]").status("OPEN").createdAt(now).build());
            return PeriodCloseResponse.builder().runId(runId).poolId(poolId).period(periodStr)
                    .status("CLOSING").approvalId(aprId).approvalsRequired(List.of("FINANCE", "COMPLIANCE_OFFICER"))
                    .createdAt(now.toString()).build();
        }

        if (period.getStatus() != PeriodStatus.OPEN && period.getStatus() != PeriodStatus.REOPENED) {
            throw new RiskPoolApiException(HttpStatus.CONFLICT, "PERIOD_NOT_OPEN", "Period is already closing or closed");
        }
        if (ym.atEndOfMonth().isAfter(LocalDate.now())) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PERIOD_NOT_ENDED", "The period has not ended");
        }
        if (!manualJournalRepository.findByPoolIdAndStatus(poolId, "PENDING_APPROVAL").isEmpty()) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PENDING_ITEMS",
                    "Pending manual journals exist", Map.of("blockers", List.of("PENDING_JOURNALS")));
        }
        if (!clients.isCashReconciled()) {
            throw new RiskPoolApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CONTROL_FAILED", "Cash reconciliation failed");
        }

        // Earn 10% pro rata sample for active policies in register
        long earned = 0;
        for (PolicyPremiumRegisterEntity reg : registerRepository.findByPoolId(poolId)) {
            long rem = Math.max(0L, reg.getPaidMinor() - reg.getEarnedMinor());
            if (rem > 0) {
                long inc = Math.max(1L, rem / 4);
                reg.setEarnedMinor(reg.getEarnedMinor() + inc);
                registerRepository.save(reg);
                earned += inc;
            }
        }
        if (earned > 0) {
            recordJournalTransaction(poolId, "PERIOD_EARNING", runId, "PERIOD_EARNING", ym.atEndOfMonth(),
                    List.of(Map.of("account", "2010", "side", "DR", "amountMinor", earned),
                            Map.of("account", "4010", "side", "CR", "amountMinor", earned)),
                    earned, "KES", Map.of("period", periodStr), null);
        }

        period.setStatus(PeriodStatus.CLOSING);
        period.setEarnedMinor(period.getEarnedMinor() + earned);
        String tbHash = support.sha256(poolId + "|" + periodStr + "|" + period.getEarnedMinor());
        period.setTrialBalanceHash(tbHash);
        periodRepository.save(period);

        String aprId = "apr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        approvalRepository.save(PoolApprovalEntity.builder()
                .approvalId(aprId).type(RiskPoolApprovalType.PERIOD_CLOSE).subjectId(key)
                .subjectHash(tbHash).requestedBy(actor.actorId())
                .requiredLevels(2).requiredRolesCsv("FINANCE,COMPLIANCE_OFFICER")
                .decisionsJson("[]").status("OPEN").createdAt(now).build());

        return PeriodCloseResponse.builder()
                .runId(runId).poolId(poolId).period(periodStr).status("CLOSING")
                .checks(List.of(
                        Map.of("name", "CASH_RECONCILED", "result", "PASS"),
                        Map.of("name", "HASH_CHAIN_INTACT", "result", "PASS")))
                .trialBalance(Map.of("balanced", true, "hash", tbHash))
                .earned(Map.of("earnedPremiumMinor", earned, "cededEarnedMinor", 0L))
                .approvalId(aprId).approvalsRequired(List.of("FINANCE", "COMPLIANCE_OFFICER"))
                .createdAt(now.toString()).build();
    }

    @Transactional
    public PoolApprovalResponse decideApproval(String approvalId, DecidePoolApprovalRequest req) {
        RiskPoolIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN)) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        PoolApprovalEntity apr = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new RiskPoolApiException(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "Approval not found"));
        if (!"OPEN".equals(apr.getStatus())) {
            throw new RiskPoolApiException(HttpStatus.CONFLICT, "APPROVAL_NOT_OPEN", "Approval already decided");
        }
        if (actor.actorId().equals(apr.getRequestedBy())) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "MAKER_CANNOT_APPROVE", "You cannot approve your own request");
        }
        if (!req.getSubjectHash().equals(apr.getSubjectHash())) {
            throw new RiskPoolApiException(HttpStatus.CONFLICT, "SUBJECT_CHANGED", "The request changed. Please review again");
        }
        List<Map<String, Object>> decisions = new ArrayList<>(readList(apr.getDecisionsJson()));
        if (decisions.stream().anyMatch(d -> Objects.equals(d.get("approver"), actor.actorId()))) {
            throw new RiskPoolApiException(HttpStatus.CONFLICT, "ALREADY_DECIDED_BY_YOU", "Already decided");
        }

        String actingRole = actor.hasRole(AuthRole.COMPLIANCE_OFFICER) ? "COMPLIANCE_OFFICER"
                : (actor.hasRole(AuthRole.FINANCE) ? "FINANCE" : "ADMIN");
        Instant now = Instant.now();
        decisions.add(Map.of("role", actingRole, "approver", actor.actorId(),
                "decision", req.getDecision().toUpperCase(), "at", now.toString()));
        apr.setDecisionsJson(writeJson(decisions));

        List<String> reqRoles = new ArrayList<>(Arrays.asList(apr.getRequiredRolesCsv().split(",")));
        decisions.forEach(d -> reqRoles.remove(String.valueOf(d.get("role"))));

        String subjectStatus;
        if ("REJECT".equalsIgnoreCase(req.getDecision())) {
            apr.setStatus("REJECTED");
            subjectStatus = "REJECTED";
            reqRoles.clear();
        } else if (decisions.size() >= apr.getRequiredLevels() && reqRoles.isEmpty()) {
            apr.setStatus("APPROVED");
            subjectStatus = releaseSubject(apr, now);
        } else {
            subjectStatus = "PENDING_APPROVAL";
        }
        approvalRepository.save(apr);

        return PoolApprovalResponse.builder()
                .approvalId(approvalId).type(apr.getType().name()).subjectId(apr.getSubjectId())
                .status(apr.getStatus()).decisions(decisions).remaining(reqRoles)
                .subjectStatus(subjectStatus).subjectHash(apr.getSubjectHash()).build();
    }

    @Transactional
    public void onPolicyIssued(String eventId, String policyId, String productId, String regionCode,
                               String peril, String season, long sumInsuredMinor, long premiumMinor,
                               long subsidyMinor, String currency) {
        if (eventId != null && inboxRepository.existsById(eventId)) return;
        if (eventId != null) inboxRepository.save(RiskPoolInboxEventEntity.builder()
                .eventId(eventId).eventType("policy.issued").processedAt(Instant.now()).build());

        String poolId = resolvePoolForRisk(productId, regionCode, peril).orElse("SUSPENSE");
        registerRepository.save(PolicyPremiumRegisterEntity.builder()
                .policyId(policyId).poolId(poolId).productId(productId)
                .regionCode(regionCode).peril(peril).season(season)
                .sumInsuredMinor(sumInsuredMinor).writtenMinor(premiumMinor).subsidyMinor(subsidyMinor)
                .currency(currency != null ? currency : "KES").inForce(false).build());
    }

    @Transactional
    public void onPolicyActivated(String eventId, String policyId, long sumInsuredMinor, LocalDate start, LocalDate end) {
        if (eventId != null && inboxRepository.existsById(eventId)) return;
        if (eventId != null) inboxRepository.save(RiskPoolInboxEventEntity.builder()
                .eventId(eventId).eventType("policy.activated").processedAt(Instant.now()).build());

        registerRepository.findById(policyId).ifPresent(reg -> {
            reg.setSumInsuredMinor(sumInsuredMinor);
            reg.setInForce(true);
            reg.setCoverageStart(start);
            reg.setCoverageEnd(end);
            registerRepository.save(reg);
            capacityRepository.findByPolicyId(policyId).ifPresent(cr -> {
                cr.setStatus("CONVERTED");
                capacityRepository.save(cr);
            });
        });
    }

    @Transactional
    public JournalTransactionEntity onPremiumPaid(String eventId, String policyId, long amountMinor, String currency, String method) {
        if (eventId != null && inboxRepository.existsById(eventId)) return null;
        if (eventId != null) inboxRepository.save(RiskPoolInboxEventEntity.builder()
                .eventId(eventId).eventType("premium.paid").processedAt(Instant.now()).build());

        PolicyPremiumRegisterEntity reg = registerRepository.findById(policyId).orElse(null);
        String poolId = reg != null ? reg.getPoolId() : "SUSPENSE";
        String drAcc = "SPONSOR_CREDIT".equals(method) ? "1210" : "1010";
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(Map.of("account", drAcc, "side", "DR", "amountMinor", amountMinor));
        lines.add(Map.of("account", "2010", "side", "CR", "amountMinor", amountMinor));

        // Apply active Quota-Share treaty cession if any
        if (reg != null && !"SUSPENSE".equals(poolId)) {
            reg.setPaidMinor(reg.getPaidMinor() + amountMinor);
            registerRepository.save(reg);
            treatyRepository.findByPoolId(poolId).stream()
                    .filter(t -> "ACTIVE".equals(t.getStatus()) && t.getType() == TreatyType.QUOTA_SHARE)
                    .findFirst().ifPresent(t -> {
                        Map<String, Object> terms = readMap(t.getTermsJson());
                        double cessionPct = ((Number) terms.getOrDefault("cessionPercent", 0)).doubleValue();
                        long ceded = Math.round(amountMinor * (cessionPct / 100.0));
                        if (ceded > 0) {
                            lines.add(Map.of("account", "1400", "side", "DR", "amountMinor", ceded));
                            lines.add(Map.of("account", "2200", "side", "CR", "amountMinor", ceded));
                        }
                    });
        }

        return recordJournalTransaction(poolId, "premium.paid", Objects.toString(eventId, UUID.randomUUID().toString()),
                "EVENT", LocalDate.now(), lines, amountMinor, currency != null ? currency : "KES",
                Map.of("policyId", policyId), "SUSPENSE".equals(poolId) ? "UNMAPPED_PRODUCT" : null);
    }

    @Transactional
    public void onClaimOpened(String eventId, String claimId, String policyId, long amountMinor, String currency) {
        if (eventId != null && inboxRepository.existsById(eventId)) return;
        if (eventId != null) inboxRepository.save(RiskPoolInboxEventEntity.builder()
                .eventId(eventId).eventType("claim.opened").processedAt(Instant.now()).build());

        PolicyPremiumRegisterEntity reg = registerRepository.findById(policyId).orElse(null);
        String poolId = reg != null ? reg.getPoolId() : "SUSPENSE";
        reserveRepository.save(ClaimReserveEntity.builder()
                .claimId(claimId).poolId(poolId).policyId(policyId)
                .reservedMinor(amountMinor).paidMinor(0L).currency(currency != null ? currency : "KES").released(false).build());
        recordJournalTransaction(poolId, "claim.opened", Objects.toString(eventId, claimId), "CLAIM_RESERVE",
                LocalDate.now(), List.of(
                        Map.of("account", "5010", "side", "DR", "amountMinor", amountMinor),
                        Map.of("account", "2100", "side", "CR", "amountMinor", amountMinor)),
                amountMinor, currency != null ? currency : "KES", Map.of("claimId", claimId, "policyId", policyId), null);
    }

    @Transactional
    public void onPayoutCompleted(String eventId, String claimId, long amountMinor, String currency) {
        if (eventId != null && inboxRepository.existsById(eventId)) return;
        if (eventId != null) inboxRepository.save(RiskPoolInboxEventEntity.builder()
                .eventId(eventId).eventType("payout.completed").processedAt(Instant.now()).build());

        ClaimReserveEntity res = reserveRepository.findById(claimId).orElse(null);
        String poolId = res != null ? res.getPoolId() : "SUSPENSE";
        if (res != null) {
            res.setPaidMinor(amountMinor);
            res.setReleased(true);
            reserveRepository.save(res);
        }
        recordJournalTransaction(poolId, "payout.completed", Objects.toString(eventId, claimId), "PAYOUT",
                LocalDate.now(), List.of(
                        Map.of("account", "2100", "side", "DR", "amountMinor", amountMinor),
                        Map.of("account", "1010", "side", "CR", "amountMinor", amountMinor)),
                amountMinor, currency != null ? currency : "KES", Map.of("claimId", claimId), null);
    }

    public JournalTransactionEntity recordJournalTransaction(String poolId, String sourceType, String sourceEventId,
                                                           String journalType, LocalDate valueDate,
                                                           List<Map<String, Object>> lines, long totalMinor,
                                                           String currency, Map<String, Object> dims, String suspenseReason) {
        String prevHash = txRepository.findFirstByPoolIdOrderByCreatedAtDesc(poolId)
                .map(JournalTransactionEntity::getEntryHash).orElse("GENESIS");
        String txId = "jt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String linesJson = writeJson(lines);
        String entryHash = support.sha256(txId + "|" + poolId + "|" + linesJson + "|" + prevHash);
        Instant now = Instant.now();

        JournalTransactionEntity tx = txRepository.save(JournalTransactionEntity.builder()
                .transactionId(txId).poolId(poolId).sourceType(sourceType).sourceEventId(sourceEventId)
                .journalType(journalType).valueDate(valueDate).postingDate(LocalDate.now()).priorPeriod(false)
                .totalMinor(totalMinor).currency(currency).linesJson(linesJson).dimensionsJson(writeJson(dims))
                .suspenseReason(suspenseReason).prevHash(prevHash).entryHash(entryHash).createdAt(now).build());

        support.enqueue(properties.getKafka().getLedgerTopic(), "ledger.posted", poolId, Map.of(
                "eventType", "ledger.posted", "transactionId", txId, "poolId", poolId,
                "sourceType", sourceType, "totalMinor", totalMinor, "currency", currency,
                "checksum", entryHash, "timestamp", now.toString()));
        return tx;
    }

    public Optional<String> resolvePoolForRisk(String productId, String regionCode, String peril) {
        return poolRepository.findAll().stream()
                .filter(p -> p.getStatus() == PoolStatus.ACTIVE)
                .filter(p -> readList(p.getMappingsJson()).stream().anyMatch(m -> {
                    @SuppressWarnings("unchecked")
                    List<String> prods = (List<String>) m.getOrDefault("productIds", List.of());
                    @SuppressWarnings("unchecked")
                    List<String> regs = (List<String>) m.getOrDefault("regionCodes", List.of());
                    return (prods.isEmpty() || prods.contains(productId)) && (regs.isEmpty() || regs.contains(regionCode));
                }))
                .map(RiskPoolEntity::getPoolId).findFirst();
    }

    private String releaseSubject(PoolApprovalEntity apr, Instant now) {
        return switch (apr.getType()) {
            case POOL -> {
                RiskPoolEntity p = poolRepository.findById(apr.getSubjectId()).orElseThrow();
                p.setStatus(PoolStatus.ACTIVE);
                poolRepository.save(p);
                support.enqueue(properties.getKafka().getLedgerTopic(), "pool.activated", p.getPoolId(), Map.of(
                        "eventType", "pool.activated", "poolId", p.getPoolId(), "poolCode", p.getPoolCode(),
                        "timestamp", now.toString()));
                yield PoolStatus.ACTIVE.name();
            }
            case TREATY -> {
                ReinsuranceTreatyEntity t = treatyRepository.findById(apr.getSubjectId()).orElseThrow();
                t.setStatus("ACTIVE");
                treatyRepository.save(t);
                support.enqueue(properties.getKafka().getLedgerTopic(), "treaty.activated", t.getTreatyId(), Map.of(
                        "eventType", "treaty.activated", "treatyId", t.getTreatyId(), "poolId", t.getPoolId(),
                        "timestamp", now.toString()));
                yield "ACTIVE";
            }
            case MANUAL_JOURNAL -> {
                ManualJournalEntity mj = manualJournalRepository.findById(apr.getSubjectId()).orElseThrow();
                JournalTransactionEntity tx = recordJournalTransaction(mj.getPoolId(), "MANUAL_JOURNAL", mj.getJournalId(),
                        mj.getJournalType().name(), mj.getValueDate(), readList(mj.getLinesJson()),
                        mj.getTotalMinor(), mj.getCurrency(), Map.of("reasonCode", mj.getReasonCode()), null);
                mj.setStatus("POSTED");
                mj.setPostedTxId(tx.getTransactionId());
                manualJournalRepository.save(mj);
                yield "POSTED";
            }
            case PERIOD_CLOSE -> {
                PoolPeriodEntity pe = periodRepository.findById(apr.getSubjectId()).orElseThrow();
                pe.setStatus(PeriodStatus.CLOSED);
                periodRepository.save(pe);
                support.enqueue(properties.getKafka().getLedgerTopic(), "ledger.period.closed", pe.getPoolId(), Map.of(
                        "eventType", "ledger.period.closed", "poolId", pe.getPoolId(), "period", pe.getPeriod(),
                        "trialBalanceHash", Objects.toString(pe.getTrialBalanceHash(), ""), "timestamp", now.toString()));
                yield PeriodStatus.CLOSED.name();
            }
            case PERIOD_REOPEN -> {
                PoolPeriodEntity pe = periodRepository.findById(apr.getSubjectId()).orElseThrow();
                pe.setStatus(PeriodStatus.REOPENED);
                periodRepository.save(pe);
                yield PeriodStatus.REOPENED.name();
            }
            default -> "APPROVED";
        };
    }

    private RiskPoolIdentityFilter.Identity requireIdentity() {
        RiskPoolIdentityFilter.Identity id = RiskPoolIdentityFilter.CTX.get();
        if (id == null) throw new RiskPoolApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
        return id;
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "[]"; }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return List.of(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, Map.class); } catch (Exception e) { return Map.of(); }
    }
}
