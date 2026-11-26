package com.umar.trigger.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.DelegationScope;
import com.umar.enums.trigger.*;
import com.umar.payload.request.trigger.EvaluateTriggerRequest;
import com.umar.payload.request.trigger.ReviewTriggerDecisionRequest;
import com.umar.payload.response.trigger.TriggerDecisionDetailResponse;
import com.umar.payload.response.trigger.TriggerEvaluateResponse;
import com.umar.payload.response.trigger.TriggerEventsResponse;
import com.umar.trigger.client.TriggerExternalClients;
import com.umar.trigger.config.TriggerProperties;
import com.umar.trigger.domain.*;
import com.umar.trigger.exception.TriggerApiException;
import com.umar.trigger.repository.*;
import com.umar.trigger.security.TriggerIdentity;
import com.umar.trigger.security.TriggerSecurityContext;
import com.umar.trigger.service.support.TriggerOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TriggerEvaluationService {

    private final TriggerRuleRepository ruleRepository;
    private final TriggerEvaluationRepository evaluationRepository;
    private final TriggerDecisionRepository decisionRepository;
    private final PolicyBindingRepository policyBindingRepository;
    private final ObservationArchiveRepository observationRepository;
    private final TriggerEventSummaryRepository eventSummaryRepository;
    private final TriggerInboxEventRepository inboxRepository;
    private final TriggerExternalClients clients;
    private final TriggerOutboxService outboxService;
    private final TriggerProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public TriggerEvaluateResponse evaluate(EvaluateTriggerRequest req) {
        TriggerIdentity actor = TriggerSecurityContext.require();
        boolean isSystem = actor.isServiceCaller();
        if (!isSystem && !actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.ADMIN)) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if ((req.getEvaluationType() == EvaluationType.MANUAL || req.getEvaluationType() == EvaluationType.BACKFILL)
                && (req.getReason() == null || req.getReason().isBlank())) {
            throw new TriggerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "reason is required for MANUAL or BACKFILL evaluations");
        }

        TriggerRuleEntity rule;
        if (req.getRuleVersion() != null) {
            rule = ruleRepository.findById(new TriggerRuleEntity.RuleVersionId(req.getRuleId(), req.getRuleVersion()))
                    .orElseThrow(() -> new TriggerApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Rule not found"));
        } else {
            rule = req.getEvaluationType() == EvaluationType.DRY_RUN
                    ? ruleRepository.findFirstByRuleIdOrderByVersionNumDesc(req.getRuleId())
                        .orElseThrow(() -> new TriggerApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Rule not found"))
                    : ruleRepository.findFirstByRuleIdAndStatusOrderByVersionNumDesc(req.getRuleId(), RuleStatus.ACTIVE)
                        .orElseThrow(() -> new TriggerApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "No active rule found"));
        }
        if (req.getEvaluationType() != EvaluationType.DRY_RUN && rule.getStatus() != RuleStatus.ACTIVE) {
            throw new TriggerApiException(HttpStatus.CONFLICT, "RULE_NOT_ACTIVE", "Rule is not ACTIVE");
        }

        String windowId = req.getWindowId() != null && !req.getWindowId().isBlank()
                ? req.getWindowId()
                : req.getWindowStart() + "_" + req.getWindowEnd();

        if (!clients.tryAcquireEvalLock(rule.getRuleId(), windowId)) {
            throw new TriggerApiException(HttpStatus.CONFLICT, "EVALUATION_IN_PROGRESS",
                    "An evaluation is already running for this rule and window");
        }

        try {
            String evalId = "ev_" + clients.sha256(rule.getRuleId() + ":" + rule.getVersionNum() + ":" + windowId).substring(0, 10);
            Optional<TriggerEvaluationEntity> existingEval = evaluationRepository.findById(evalId);
            if (existingEval.isPresent() && req.getEvaluationType() == EvaluationType.SCHEDULED) {
                TriggerEvaluationEntity ev = existingEval.get();
                return TriggerEvaluateResponse.builder()
                        .evaluationId(ev.getEvaluationId())
                        .evaluationType(ev.getEvaluationType().name())
                        .ruleId(ev.getRuleId())
                        .ruleVersion(ev.getRuleVersion())
                        .status(ev.getStatus())
                        .evaluatedCount(ev.getEvaluatedCount())
                        .breachedCount(ev.getBreachedCount())
                        .reviewRequiredCount(ev.getReviewRequiredCount())
                        .completedAt(ev.getCompletedAt().toString())
                        .build();
            }

            List<Map<String, Object>> tiers = readList(rule.getTiersJson());
            List<PolicyBindingEntity> bindings = policyBindingRepository.findByRuleIdAndActiveTrue(rule.getRuleId())
                    .stream()
                    .filter(b -> req.getRegionCodes() == null || req.getRegionCodes().isEmpty()
                            || req.getRegionCodes().contains(b.getRegionCode()))
                    .filter(b -> req.getPlotIds() == null || req.getPlotIds().isEmpty()
                            || req.getPlotIds().contains(b.getPlotId()))
                    .toList();

            boolean isLarge = bindings.size() >= properties.getRules().getLargeEventThreshold();
            boolean conflict = clients.isForceCorroborationConflict();
            int breached = 0;
            int reviewReq = 0;
            long totalIndicated = 0L;
            List<Map<String, Object>> samples = new ArrayList<>();
            Instant now = Instant.now();

            for (PolicyBindingEntity b : bindings) {
                double observedVal = clients.fetchRegionIndexValue(b.getRegionCode());
                Map<String, Object> matchedTier = selectMatchedTier(tiers, observedVal);
                TriggerDecisionOutcome outcome = matchedTier != null ? TriggerDecisionOutcome.BREACH : TriggerDecisionOutcome.NO_BREACH;
                double payoutPct = matchedTier != null ? ((Number) matchedTier.get("payoutPercent")).doubleValue() : 0.0;
                double thresholdVal = matchedTier != null
                        ? ((Number) matchedTier.get("threshold")).doubleValue()
                        : ((Number) tiers.getFirst().get("threshold")).doubleValue();
                long indicatedMinor = Math.round(b.getSumInsuredMinor() * (payoutPct / 100.0));
                String tierId = matchedTier != null ? Objects.toString(matchedTier.get("tierId"), "T1") : null;

                DataSourceQuality quality = conflict ? DataSourceQuality.CONFLICTING : DataSourceQuality.CORROBORATED;
                boolean needsReview = (outcome == TriggerDecisionOutcome.BREACH) && (isLarge || conflict);
                TriggerDecisionStatus decStatus = needsReview ? TriggerDecisionStatus.PENDING_REVIEW : TriggerDecisionStatus.FINAL;

                if (outcome == TriggerDecisionOutcome.BREACH) {
                    breached++;
                    totalIndicated += indicatedMinor;
                }
                if (needsReview) {
                    reviewReq++;
                }

                if (req.getEvaluationType() != EvaluationType.DRY_RUN) {
                    String decId = "dec_" + clients.sha256(b.getPolicyId() + ":" + windowId + ":" + rule.getRuleId()).substring(0, 10);
                    List<Map<String, Object>> evPoints = List.of(
                            Map.of("source", "CHIRPS_V2", "value", observedVal, "unit", "mm"),
                            Map.of("source", "IOT_CORROBORATION", "status", quality.name())
                    );
                    String evJson = writeJson(evPoints);
                    String evHash = clients.sha256(evJson);
                    String bundleToken = clients.storeEvidenceBundle(decId, evJson);
                    if (bundleToken == null) bundleToken = "vt_ev_offline";

                    TriggerDecisionEntity entity = decisionRepository.findByPolicyIdAndWindowIdAndRuleId(
                                    b.getPolicyId(), windowId, rule.getRuleId())
                            .orElse(TriggerDecisionEntity.builder()
                                    .decisionId(decId)
                                    .evaluationId(evalId)
                                    .policyId(b.getPolicyId())
                                    .farmerId(b.getFarmerId())
                                    .plotId(b.getPlotId())
                                    .regionCode(b.getRegionCode())
                                    .ruleId(rule.getRuleId())
                                    .ruleVersion(rule.getVersionNum())
                                    .ruleHash(rule.getDefinitionHash())
                                    .windowId(windowId)
                                    .windowStart(req.getWindowStart())
                                    .windowEnd(req.getWindowEnd())
                                    .currency(b.getCurrency())
                                    .historyJson("[]")
                                    .versionNum(1)
                                    .build());

                    entity.setOutcome(outcome);
                    entity.setStatus(decStatus);
                    entity.setTierId(tierId);
                    entity.setPayoutPercent(payoutPct);
                    entity.setIndicatedPayoutMinor(indicatedMinor);
                    entity.setComputedIndexValue(observedVal);
                    entity.setThresholdValue(thresholdVal);
                    entity.setDataQuality(quality);
                    entity.setEvidenceHash(evHash);
                    entity.setEvidenceBundleToken(bundleToken);
                    entity.setEvidenceJson(evJson);
                    entity.setPhasesJson(Objects.toString(rule.getPhasesJson(), "[]"));
                    entity.setCorroborationJson(writeJson(Map.of("quality", quality.name(), "conflict", conflict)));
                    entity.setLargeEvent(isLarge);
                    entity.setDecidedAt(now);
                    decisionRepository.save(entity);

                    if (outcome == TriggerDecisionOutcome.BREACH && decStatus == TriggerDecisionStatus.FINAL) {
                        publishThresholdBreached(entity, rule, now);
                    } else if (needsReview) {
                        outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.review.required", entity.getDecisionId(), Map.of(
                                "eventType", "trigger.review.required",
                                "decisionId", entity.getDecisionId(), "ruleId", rule.getRuleId(),
                                "largeEvent", isLarge, "dataQuality", quality.name(), "timestamp", now.toString()));
                    }
                    samples.add(Map.of("decisionId", entity.getDecisionId(), "policyId", b.getPolicyId(),
                            "outcome", outcome.name(), "status", decStatus.name(), "payoutPercent", payoutPct));
                } else {
                    samples.add(Map.of("policyId", b.getPolicyId(), "outcome", outcome.name(),
                            "status", "DRY_RUN", "payoutPercent", payoutPct));
                }
            }

            if (req.getEvaluationType() != EvaluationType.DRY_RUN) {
                evaluationRepository.save(TriggerEvaluationEntity.builder()
                        .evaluationId(evalId)
                        .ruleId(rule.getRuleId())
                        .ruleVersion(rule.getVersionNum())
                        .evaluationType(req.getEvaluationType())
                        .regionCodesCsv(req.getRegionCodes() != null ? String.join(",", req.getRegionCodes()) : rule.getRegionCodesCsv())
                        .windowId(windowId)
                        .windowStart(req.getWindowStart())
                        .windowEnd(req.getWindowEnd())
                        .status("COMPLETED")
                        .evaluatedCount(bindings.size())
                        .breachedCount(breached)
                        .reviewRequiredCount(reviewReq)
                        .reason(req.getReason())
                        .triggeredBy(actor.actorId())
                        .startedAt(now)
                        .completedAt(now)
                        .build());

                String evtSummaryId = "tevt_" + evalId;
                String reg = (req.getRegionCodes() != null && !req.getRegionCodes().isEmpty())
                        ? req.getRegionCodes().getFirst() : rule.getRegionCodesCsv().split(",")[0];
                eventSummaryRepository.save(TriggerEventSummaryEntity.builder()
                        .eventId(evtSummaryId)
                        .ruleId(rule.getRuleId())
                        .ruleVersion(rule.getVersionNum())
                        .peril(rule.getPeril().name())
                        .regionCode(reg)
                        .windowId(windowId)
                        .status(reviewReq > 0 ? "REVIEW_REQUIRED" : (breached > 0 ? "BREACHED" : "NO_BREACH"))
                        .affectedPolicies(bindings.size())
                        .breachedPolicies(breached)
                        .indicatedPayoutMinor(totalIndicated)
                        .currency("KES")
                        .updatedAt(now)
                        .build());

                outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.evaluated", evalId, Map.of(
                        "eventType", "trigger.evaluated", "evaluationId", evalId, "ruleId", rule.getRuleId(),
                        "evaluatedCount", bindings.size(), "breachedCount", breached, "timestamp", now.toString()));

                outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.event.status.changed", evtSummaryId, Map.of(
                        "eventType", "trigger.event.status.changed", "eventId", evtSummaryId,
                        "status", reviewReq > 0 ? "REVIEW_REQUIRED" : "FINAL",
                        "affectedPolicies", bindings.size(), "indicatedPayoutMinor", totalIndicated,
                        "regionCodes", List.of(reg), "peril", rule.getPeril().name(), "timestamp", now.toString()));
            }

            return TriggerEvaluateResponse.builder()
                    .evaluationId(evalId)
                    .evaluationType(req.getEvaluationType().name())
                    .ruleId(rule.getRuleId())
                    .ruleVersion(rule.getVersionNum())
                    .status("COMPLETED")
                    .evaluatedCount(bindings.size())
                    .breachedCount(breached)
                    .reviewRequiredCount(reviewReq)
                    .summary(Map.of("indicatedPayoutMinor", totalIndicated, "largeEvent", isLarge))
                    .sampleDecisions(samples.size() > 10 ? samples.subList(0, 10) : samples)
                    .completedAt(now.toString())
                    .build();
        } finally {
            clients.releaseEvalLock(rule.getRuleId(), windowId);
        }
    }

    public TriggerDecisionDetailResponse getDecision(String decisionId, String include, String delegationHeader) {
        TriggerIdentity actor = TriggerSecurityContext.require();
        TriggerDecisionEntity dec = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new TriggerApiException(HttpStatus.NOT_FOUND, "DECISION_NOT_FOUND", "Decision not found"));

        boolean isStaff = actor.isServiceCaller() || actor.hasAnyRole(
                AuthRole.UNDERWRITER, AuthRole.TRIGGER_REVIEWER, AuthRole.CLAIMS_REVIEWER,
                AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR, AuthRole.SUPPORT, AuthRole.ADMIN);
        boolean isOwner = actor.hasRole(AuthRole.FARMER) && actor.actorId().equals(dec.getFarmerId());
        boolean isDelegatedAgent = false;
        if (!isStaff && !isOwner && actor.hasRole(AuthRole.AGENT) && delegationHeader != null) {
            try {
                isDelegatedAgent = clients.validateDelegation(UUID.fromString(delegationHeader),
                        UUID.fromString(actor.actorId()), UUID.fromString(dec.getFarmerId()), DelegationScope.POLICY_ASSIST);
            } catch (IllegalArgumentException ignored) {}
        }
        if (!isStaff && !isOwner && !isDelegatedAgent) {
            throw new TriggerApiException(HttpStatus.NOT_FOUND, "DECISION_NOT_FOUND", "Decision not found");
        }

        if (include != null && include.contains("verify")) {
            String bundle = clients.getEvidenceBundle(dec.getEvidenceBundleToken());
            if (bundle == null || !clients.sha256(bundle).equals(dec.getEvidenceHash())) {
                throw new TriggerApiException(HttpStatus.CONFLICT, "EVIDENCE_MISMATCH",
                        "Stored evidence hash does not match Vault bundle");
            }
        }

        String explanation = String.format("Observed index %.1f against threshold %.1f for window %s (%s).",
                dec.getComputedIndexValue(), dec.getThresholdValue(), dec.getWindowId(), dec.getOutcome().name());

        return TriggerDecisionDetailResponse.builder()
                .decisionId(dec.getDecisionId())
                .evaluationId(dec.getEvaluationId())
                .policyId(dec.getPolicyId())
                .plotId(dec.getPlotId())
                .regionCode(dec.getRegionCode())
                .ruleId(dec.getRuleId())
                .ruleVersion(dec.getRuleVersion())
                .ruleHash(isStaff ? dec.getRuleHash() : null)
                .outcome(dec.getOutcome().name())
                .status(dec.getStatus().name())
                .tierId(dec.getTierId())
                .payoutPercent(dec.getPayoutPercent())
                .indicatedPayoutMinor(dec.getIndicatedPayoutMinor())
                .currency(dec.getCurrency())
                .window(Map.of("windowId", dec.getWindowId(),
                        "start", dec.getWindowStart().toString(),
                        "end", dec.getWindowEnd().toString()))
                .computedIndexValue(dec.getComputedIndexValue())
                .thresholdValue(dec.getThresholdValue())
                .dataQuality(isStaff ? dec.getDataQuality().name() : null)
                .evidenceHash(isStaff ? dec.getEvidenceHash() : null)
                .evidenceBundleToken(isStaff ? dec.getEvidenceBundleToken() : null)
                .explanation(explanation)
                .evidence(isStaff ? readList(dec.getEvidenceJson()) : null)
                .phases(isStaff ? readList(dec.getPhasesJson()) : null)
                .corroboration(isStaff ? readMap(dec.getCorroborationJson()) : null)
                .history(isStaff ? readList(dec.getHistoryJson()) : null)
                .decidedAt(dec.getDecidedAt().toString())
                .version(dec.getVersionNum())
                .build();
    }

    public TriggerEventsResponse listEvents(String status, String peril, String ruleId, String regionCode,
                                            Integer minAffected, int page, int size) {
        TriggerIdentity actor = TriggerSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.TRIGGER_REVIEWER, AuthRole.COMPLIANCE_OFFICER,
                AuthRole.AUDITOR, AuthRole.FINANCE, AuthRole.ADMIN, AuthRole.REINSURANCE_MANAGER)) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        List<TriggerEventSummaryEntity> filtered = eventSummaryRepository.findAll().stream()
                .filter(e -> status == null || e.getStatus().equalsIgnoreCase(status))
                .filter(e -> peril == null || e.getPeril().equalsIgnoreCase(peril))
                .filter(e -> ruleId == null || e.getRuleId().equals(ruleId))
                .filter(e -> regionCode == null || e.getRegionCode().equalsIgnoreCase(regionCode))
                .filter(e -> minAffected == null || e.getAffectedPolicies() >= minAffected)
                .sorted(Comparator.comparing(TriggerEventSummaryEntity::getUpdatedAt).reversed())
                .toList();

        int fromIdx = Math.min(page * size, filtered.size());
        int toIdx = Math.min(fromIdx + size, filtered.size());
        List<Map<String, Object>> pageItems = filtered.subList(fromIdx, toIdx).stream()
                .map(e -> Map.<String, Object>ofEntries(
                        Map.entry("eventId", e.getEventId()),
                        Map.entry("ruleId", e.getRuleId()),
                        Map.entry("ruleVersion", e.getRuleVersion()),
                        Map.entry("peril", e.getPeril()),
                        Map.entry("regionCode", e.getRegionCode()),
                        Map.entry("windowId", e.getWindowId()),
                        Map.entry("status", e.getStatus()),
                        Map.entry("affectedPolicies", e.getAffectedPolicies()),
                        Map.entry("breachedPolicies", e.getBreachedPolicies()),
                        Map.entry("indicatedPayoutMinor", e.getIndicatedPayoutMinor()),
                        Map.entry("currency", e.getCurrency())))
                .toList();

        long totalPayout = filtered.stream().mapToLong(TriggerEventSummaryEntity::getIndicatedPayoutMinor).sum();
        return TriggerEventsResponse.builder()
                .events(pageItems)
                .summary(Map.of("totalEvents", filtered.size(), "totalIndicatedPayoutMinor", totalPayout))
                .page(page)
                .size(size)
                .total(filtered.size())
                .build();
    }

    @Transactional
    public TriggerDecisionDetailResponse reviewDecision(String decisionId, ReviewTriggerDecisionRequest req) {
        TriggerIdentity actor = TriggerSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.TRIGGER_REVIEWER, AuthRole.ADMIN)) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (req.getNote() == null || req.getNote().trim().length() < 20) {
            throw new TriggerApiException(HttpStatus.BAD_REQUEST, "MISSING_JUSTIFICATION", "Note must be at least 20 characters");
        }

        TriggerDecisionEntity dec = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new TriggerApiException(HttpStatus.NOT_FOUND, "DECISION_NOT_FOUND", "Decision not found"));

        TriggerRuleEntity rule = ruleRepository.findById(new TriggerRuleEntity.RuleVersionId(dec.getRuleId(), dec.getRuleVersion()))
                .orElse(null);
        if (rule != null && actor.actorId().equals(rule.getAuthorId())) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "REVIEWER_CONFLICT", "Rule author cannot review decision");
        }
        if (actor.actorId().equals(dec.getFirstReviewerId())) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "REVIEWER_CONFLICT",
                    "First reviewer cannot perform second review");
        }

        Instant now = Instant.now();
        List<Map<String, Object>> history = new ArrayList<>(readList(dec.getHistoryJson()));
        history.add(Map.of("action", req.getAction().name(), "reviewer", actor.actorId(),
                "note", req.getNote(), "at", now.toString()));
        dec.setHistoryJson(writeJson(history));
        dec.setVersionNum(dec.getVersionNum() + 1);

        boolean needsSecondReview = (dec.isLargeEvent() || dec.getDataQuality() == DataSourceQuality.CONFLICTING)
                && dec.getFirstReviewerId() == null;

        if (needsSecondReview) {
            dec.setFirstReviewerId(actor.actorId());
            dec.setStatus(TriggerDecisionStatus.PENDING_SECOND_REVIEW);
            decisionRepository.save(dec);
            return getDecision(dec.getDecisionId(), "explanation,evidence,history", null);
        }

        switch (req.getAction()) {
            case CONFIRM_BREACH -> {
                dec.setOutcome(TriggerDecisionOutcome.BREACH);
                dec.setStatus(TriggerDecisionStatus.FINAL);
                if (rule != null) publishThresholdBreached(dec, rule, now);
            }
            case DISMISS_NO_BREACH -> {
                TriggerDecisionOutcome prev = dec.getOutcome();
                dec.setOutcome(TriggerDecisionOutcome.NO_BREACH);
                dec.setStatus(TriggerDecisionStatus.FINAL);
                dec.setIndicatedPayoutMinor(0L);
                dec.setPayoutPercent(0.0);
                if (prev == TriggerDecisionOutcome.BREACH) {
                    outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.decision.revised", dec.getDecisionId(), Map.of(
                            "eventType", "trigger.decision.revised",
                            "decisionId", dec.getDecisionId(), "policyId", dec.getPolicyId(),
                            "outcome", "NO_BREACH", "status", "FINAL",
                            "revisedPayoutPercent", 0.0, "timestamp", now.toString()));
                }
            }
            case VOID -> {
                dec.setOutcome(TriggerDecisionOutcome.VOID);
                dec.setStatus(TriggerDecisionStatus.VOID);
                dec.setIndicatedPayoutMinor(0L);
                outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.decision.revised", dec.getDecisionId(), Map.of(
                        "eventType", "trigger.decision.revised",
                        "decisionId", dec.getDecisionId(), "policyId", dec.getPolicyId(),
                        "outcome", "VOID", "status", "VOID",
                        "revisedPayoutPercent", 0.0, "timestamp", now.toString()));
            }
            case REEVALUATE -> dec.setStatus(TriggerDecisionStatus.PROVISIONAL);
        }

        decisionRepository.save(dec);
        outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.review.resolved", dec.getDecisionId(), Map.of(
                "eventType", "trigger.review.resolved",
                "decisionId", dec.getDecisionId(), "action", req.getAction().name(),
                "status", dec.getStatus().name(), "timestamp", now.toString()));

        return getDecision(dec.getDecisionId(), "explanation,evidence,history", null);
    }

    @Transactional
    public void onPolicyActivated(String eventId, String policyId, String farmerId, String plotId,
                                  String regionCode, String productId, String ruleId, int ruleVersion,
                                  long sumInsuredMinor, String currency) {
        if (eventId != null && inboxRepository.existsById(eventId)) return;
        if (eventId != null) {
            inboxRepository.save(TriggerInboxEventEntity.builder()
                    .eventId(eventId).eventType("policy.activated").processedAt(Instant.now()).build());
        }
        policyBindingRepository.save(PolicyBindingEntity.builder()
                .policyId(policyId)
                .farmerId(farmerId)
                .plotId(plotId)
                .regionCode(regionCode)
                .productId(productId)
                .ruleId(ruleId)
                .ruleVersion(ruleVersion)
                .sumInsuredMinor(sumInsuredMinor)
                .currency(currency != null ? currency : "KES")
                .active(true)
                .activatedAt(Instant.now())
                .build());
    }

    @Transactional
    public void onObservationIngested(String ingestId, String sourceId, String regionCode, String plotId,
                                      String channel, double value, LocalDate observedDate, String qualityFlagsCsv, int revision) {
        observationRepository.save(ObservationArchiveEntity.builder()
                .ingestId(ingestId)
                .sourceId(sourceId)
                .regionCode(regionCode)
                .plotId(plotId)
                .channel(channel)
                .metricValue(value)
                .observedDate(observedDate)
                .qualityFlagsCsv(qualityFlagsCsv)
                .revision(revision)
                .ingestedAt(Instant.now())
                .build());
    }

    private void publishThresholdBreached(TriggerDecisionEntity dec, TriggerRuleEntity rule, Instant now) {
        outboxService.enqueue(properties.getKafka().getTriggerTopic(), "threshold.breached", dec.getPolicyId(), Map.ofEntries(
                Map.entry("eventType", "threshold.breached"),
                Map.entry("decisionId", dec.getDecisionId()),
                Map.entry("policyId", dec.getPolicyId()),
                Map.entry("farmerId", dec.getFarmerId()),
                Map.entry("plotId", dec.getPlotId()),
                Map.entry("regionCode", dec.getRegionCode()),
                Map.entry("ruleId", rule.getRuleId()),
                Map.entry("ruleVersion", rule.getVersionNum()),
                Map.entry("ruleHash", rule.getDefinitionHash()),
                Map.entry("windowId", dec.getWindowId()),
                Map.entry("tierId", Objects.toString(dec.getTierId(), "T1")),
                Map.entry("payoutPercent", dec.getPayoutPercent()),
                Map.entry("indicatedPayoutMinor", dec.getIndicatedPayoutMinor()),
                Map.entry("currency", dec.getCurrency()),
                Map.entry("evidenceHash", dec.getEvidenceHash()),
                Map.entry("evidenceBundleToken", dec.getEvidenceBundleToken()),
                Map.entry("decidedAt", now.toString()),
                Map.entry("timestamp", now.toString())
        ));
    }

    private Map<String, Object> selectMatchedTier(List<Map<String, Object>> tiers, double indexVal) {
        Map<String, Object> matched = null;
        for (Map<String, Object> tier : tiers) {
            double threshold = ((Number) tier.get("threshold")).doubleValue();
            if (indexVal <= threshold) {
                matched = tier;
            }
        }
        return matched;
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
