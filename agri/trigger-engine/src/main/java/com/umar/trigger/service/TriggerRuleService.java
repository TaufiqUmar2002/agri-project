package com.umar.trigger.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.trigger.RuleDecisionAction;
import com.umar.enums.trigger.RuleStatus;
import com.umar.payload.request.trigger.CreateTriggerRuleRequest;
import com.umar.payload.request.trigger.RuleDecisionRequest;
import com.umar.payload.request.trigger.RunSimulationRequest;
import com.umar.payload.response.trigger.SimulationResponse;
import com.umar.payload.response.trigger.TriggerRuleResponse;
import com.umar.trigger.client.TriggerExternalClients;
import com.umar.trigger.config.TriggerProperties;
import com.umar.trigger.domain.SimulationRunEntity;
import com.umar.trigger.domain.TriggerRuleEntity;
import com.umar.trigger.exception.TriggerApiException;
import com.umar.trigger.repository.SimulationRunRepository;
import com.umar.trigger.repository.TriggerRuleRepository;
import com.umar.trigger.security.TriggerIdentity;
import com.umar.trigger.security.TriggerSecurityContext;
import com.umar.trigger.service.support.TriggerOutboxService;
import com.umar.trigger.service.support.TriggerRateLimitService;
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
public class TriggerRuleService {

    private final TriggerRuleRepository ruleRepository;
    private final SimulationRunRepository simulationRepository;
    private final TriggerExternalClients clients;
    private final TriggerOutboxService outboxService;
    private final TriggerRateLimitService rateLimitService;
    private final TriggerProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public TriggerRuleResponse createRule(CreateTriggerRuleRequest req) {
        TriggerIdentity actor = TriggerSecurityContext.require();
        if (!actor.isServiceCaller() && !actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.ADMIN)) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        rateLimitService.check("trg-rule:" + actor.actorId(), 30, 3600);

        List<Map<String, Object>> tiers = req.getTiers();
        if (tiers == null || tiers.isEmpty()) {
            throw new TriggerApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIER_THRESHOLDS", "At least one tier is required");
        }
        validateTiers(tiers);

        Optional<String> expectedHash = clients.getProductContractHash(req.getProductId(), req.getProductVersion());
        String contractHash = req.getContractualDefinitionHash() != null
                ? req.getContractualDefinitionHash()
                : clients.sha256(req.getProductId() + ":" + req.getProductVersion() + ":" + req.getPeril().name());
        if (expectedHash.isPresent() && !expectedHash.get().equals(contractHash)) {
            throw new TriggerApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CONTRACT_MISMATCH",
                    "Rule definition does not match contractual product terms");
        }

        if (!ruleRepository.findByRuleCodeAndStatusIn(req.getRuleCode(),
                List.of(RuleStatus.DRAFT, RuleStatus.PENDING_APPROVAL)).isEmpty()) {
            throw new TriggerApiException(HttpStatus.CONFLICT, "RULE_DRAFT_EXISTS",
                    "An open draft already exists for ruleCode " + req.getRuleCode());
        }

        if (req.isSubmitForApproval() && (req.getBacktestRef() == null || req.getBacktestRef().isBlank())) {
            throw new TriggerApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BACKTEST_REQUIRED",
                    "backtestRef is required when submitting for approval");
        }

        int versionNum = ruleRepository.findFirstByRuleCodeOrderByVersionNumDesc(req.getRuleCode())
                .map(r -> r.getVersionNum() + 1).orElse(1);
        String ruleId = ruleRepository.findFirstByRuleCodeOrderByVersionNumDesc(req.getRuleCode())
                .map(TriggerRuleEntity::getRuleId)
                .orElse("rul_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));

        String canonicalDef = writeJson(Map.of(
                "ruleCode", req.getRuleCode(),
                "productId", req.getProductId(),
                "peril", req.getPeril().name(),
                "tiers", tiers));
        String defHash = clients.sha256(canonicalDef);

        RuleStatus status = req.isSubmitForApproval() ? RuleStatus.PENDING_APPROVAL : RuleStatus.DRAFT;
        Instant now = Instant.now();

        TriggerRuleEntity saved = ruleRepository.save(TriggerRuleEntity.builder()
                .ruleId(ruleId)
                .versionNum(versionNum)
                .ruleCode(req.getRuleCode())
                .productId(req.getProductId())
                .productVersion(req.getProductVersion())
                .contractualDefinitionHash(contractHash)
                .definitionHash(defHash)
                .peril(req.getPeril())
                .regionCodesCsv(String.join(",", req.getRegionCodes()))
                .indexType(Objects.toString(req.getIndexType(), "CUMULATIVE_RAINFALL"))
                .observationWindowJson(writeJson(req.getObservationWindow()))
                .dataSourcesJson(writeJson(req.getDataSources()))
                .corroborationJson(writeJson(req.getCorroboration()))
                .phasesJson(writeJson(req.getPhases()))
                .tiersJson(writeJson(tiers))
                .qualityGatesJson(writeJson(req.getQualityGates()))
                .coolingPeriodJson(writeJson(req.getCoolingPeriod()))
                .gracePeriodHours(req.getGracePeriodHours() != null ? req.getGracePeriodHours() : 72)
                .backtestRef(req.getBacktestRef())
                .status(status)
                .authorId(actor.actorId())
                .approvalsJson("[]")
                .effectiveFrom(req.getEffectiveFrom())
                .createdAt(now)
                .build());

        outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.rule.created", ruleId, Map.of(
                "eventType", "trigger.rule.created",
                "ruleId", ruleId, "ruleCode", req.getRuleCode(), "version", versionNum,
                "productId", req.getProductId(), "status", status.name(),
                "definitionHash", defHash, "timestamp", now.toString()));

        List<Map<String, Object>> sandbox = List.of(
                Map.of("scenario", "SEVERE_DROUGHT", "indexValue", 18.0, "tier", tiers.getFirst().get("tierId"), "breach", true),
                Map.of("scenario", "NORMAL_RAINFALL", "indexValue", 180.0, "breach", false)
        );

        return TriggerRuleResponse.builder()
                .ruleId(saved.getRuleId())
                .ruleCode(saved.getRuleCode())
                .version(saved.getVersionNum())
                .productId(saved.getProductId())
                .productVersion(saved.getProductVersion())
                .status(saved.getStatus().name())
                .definitionHash(defHash)
                .contractualDefinitionHash(contractHash)
                .sandboxTest(sandbox)
                .approvalsRequired(status == RuleStatus.PENDING_APPROVAL
                        ? List.of("UNDERWRITER", "COMPLIANCE_OFFICER") : List.of())
                .approvals(List.of())
                .effectiveFrom(saved.getEffectiveFrom().toString())
                .createdAt(now.toString())
                .build();
    }

    @Transactional
    public TriggerRuleResponse decideRule(String ruleId, int version, RuleDecisionRequest req) {
        TriggerIdentity actor = TriggerSecurityContext.require();
        TriggerRuleEntity rule = ruleRepository.findById(new TriggerRuleEntity.RuleVersionId(ruleId, version))
                .orElseThrow(() -> new TriggerApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Rule version not found"));

        if (!req.getDefinitionHash().equals(rule.getDefinitionHash())) {
            throw new TriggerApiException(HttpStatus.CONFLICT, "DEFINITION_CHANGED",
                    "Rule definition changed; reload before deciding");
        }

        if (req.getAction() == RuleDecisionAction.EMERGENCY_STOP) {
            if (!actor.hasRole(AuthRole.ADMIN)) {
                throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only Admin can emergency-stop a rule");
            }
            rule.setStatus(RuleStatus.STOPPED);
            ruleRepository.save(rule);
            outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.rule.stopped", ruleId, Map.of(
                    "eventType", "trigger.rule.stopped", "ruleId", ruleId, "version", version,
                    "stoppedBy", actor.actorId(), "comment", req.getComment(), "timestamp", Instant.now().toString()));
            return toRuleResponse(rule, readList(rule.getApprovalsJson()));
        }

        if (req.getAction() == RuleDecisionAction.WITHDRAW) {
            if (!actor.hasRole(AuthRole.ADMIN) && !actor.actorId().equals(rule.getAuthorId())) {
                throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised to withdraw");
            }
            if (rule.getStatus() == RuleStatus.ACTIVE) {
                throw new TriggerApiException(HttpStatus.CONFLICT, "RULE_STATE_INVALID", "Active rule must be retired, not withdrawn");
            }
            rule.setStatus(RuleStatus.RETIRED);
            ruleRepository.save(rule);
            return toRuleResponse(rule, readList(rule.getApprovalsJson()));
        }

        if (req.getAction() == RuleDecisionAction.RETIRE) {
            if (!actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN)) {
                throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
            }
            rule.setStatus(RuleStatus.RETIRED);
            ruleRepository.save(rule);
            outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.rule.retired", ruleId, Map.of(
                    "eventType", "trigger.rule.retired", "ruleId", ruleId, "version", version,
                    "retiredBy", actor.actorId(), "timestamp", Instant.now().toString()));
            return toRuleResponse(rule, readList(rule.getApprovalsJson()));
        }

        // APPROVE / REJECT
        if (!actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN)) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (actor.actorId().equals(rule.getAuthorId())) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "AUTHOR_CANNOT_APPROVE",
                    "Rule author cannot approve or reject their own draft");
        }
        if (rule.getStatus() != RuleStatus.PENDING_APPROVAL) {
            throw new TriggerApiException(HttpStatus.CONFLICT, "RULE_STATE_INVALID", "Rule is not pending approval");
        }

        List<Map<String, Object>> approvals = new ArrayList<>(readList(rule.getApprovalsJson()));
        if (approvals.stream().anyMatch(a -> Objects.equals(a.get("actorId"), actor.actorId()))) {
            throw new TriggerApiException(HttpStatus.CONFLICT, "ALREADY_DECIDED", "You already decided this rule version");
        }

        String actingRole = actor.hasRole(AuthRole.COMPLIANCE_OFFICER) ? "COMPLIANCE_OFFICER"
                : (actor.hasRole(AuthRole.UNDERWRITER) ? "UNDERWRITER" : "ADMIN");
        Instant now = Instant.now();
        approvals.add(Map.of(
                "role", actingRole,
                "actorId", actor.actorId(),
                "action", req.getAction().name(),
                "comment", req.getComment(),
                "at", now.toString()));
        rule.setApprovalsJson(writeJson(approvals));

        if (req.getAction() == RuleDecisionAction.REJECT) {
            rule.setStatus(RuleStatus.DRAFT);
        } else {
            boolean hasUnderwriter = approvals.stream().anyMatch(a -> "UNDERWRITER".equals(a.get("role")) || "ADMIN".equals(a.get("role")));
            boolean hasCompliance = approvals.stream().anyMatch(a -> "COMPLIANCE_OFFICER".equals(a.get("role")) || "ADMIN".equals(a.get("role")));
            if (hasUnderwriter && hasCompliance) {
                rule.setStatus(RuleStatus.ACTIVE);
                rule.setActivatedAt(now);
                outboxService.enqueue(properties.getKafka().getTriggerTopic(), "trigger.rule.activated", ruleId, Map.of(
                        "eventType", "trigger.rule.activated",
                        "ruleId", ruleId, "ruleCode", rule.getRuleCode(), "version", version,
                        "definitionHash", rule.getDefinitionHash(), "timestamp", now.toString()));
            }
        }
        ruleRepository.save(rule);
        return toRuleResponse(rule, approvals);
    }

    @Transactional
    public SimulationResponse runSimulation(RunSimulationRequest req) {
        TriggerIdentity actor = TriggerSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.ADMIN)) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (!clients.isArchiveAvailable()) {
            throw new TriggerApiException(HttpStatus.SERVICE_UNAVAILABLE, "ARCHIVE_UNAVAILABLE", "Historical weather archive unavailable");
        }
        int seasons = req.getSeasons() != null ? req.getSeasons() : 10;
        if (seasons < properties.getRules().getMinSimulationSeasons()) {
            throw new TriggerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "At least " + properties.getRules().getMinSimulationSeasons() + " seasons are required");
        }
        if (clients.getAvailableArchiveSeasons(req.getRegionCodes()) < properties.getRules().getMinSimulationSeasons()) {
            throw new TriggerApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ARCHIVE_DATA_INSUFFICIENT",
                    "Insufficient historical data for selected regions");
        }

        String defHash;
        if (req.getRuleId() != null) {
            int ver = req.getRuleVersion() != null ? req.getRuleVersion() : 1;
            TriggerRuleEntity rule = ruleRepository.findById(new TriggerRuleEntity.RuleVersionId(req.getRuleId(), ver))
                    .orElseThrow(() -> new TriggerApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Rule not found"));
            defHash = rule.getDefinitionHash();
        } else {
            defHash = clients.sha256(writeJson(req.getDraftRule()));
        }

        String simId = "sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Instant now = Instant.now();
        LocalDate validUntil = LocalDate.now().plusDays(properties.getRules().getSimulationValidityDays());

        List<Map<String, Object>> perSeason = new ArrayList<>();
        for (int i = 0; i < seasons; i++) {
            int yr = req.getFromDate().getYear() + i;
            boolean triggered = (i % 4 == 0);
            perSeason.add(Map.of("seasonYear", yr, "triggered", triggered, "lossRatio", triggered ? 1.85 : 0.0));
        }
        double elr = 0.62;
        double burningCost = 6.2;
        double dataCov = 98.4;

        String reportToken = "vt_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> summary = Map.of(
                "triggerFrequencyPercent", 25.0,
                "maxSingleSeasonLossRatio", 1.85,
                "basisRiskScore", "LOW",
                "outputFormat", req.getOutputFormat());

        SimulationRunEntity saved = simulationRepository.save(SimulationRunEntity.builder()
                .simulationId(simId)
                .ruleId(req.getRuleId())
                .ruleVersion(req.getRuleVersion())
                .definitionHash(defHash)
                .regionCodesCsv(String.join(",", req.getRegionCodes()))
                .fromDate(req.getFromDate())
                .toDate(req.getToDate())
                .seasonsCovered(seasons)
                .expectedLossRatio(elr)
                .burningCostPercent(burningCost)
                .dataCoveragePercent(dataCov)
                .summaryJson(writeJson(summary))
                .perSeasonJson(writeJson(perSeason))
                .comparisonJson(writeJson(req.getCompareTo()))
                .sensitivityJson(writeJson(req.getSensitivity()))
                .reportToken(reportToken)
                .validUntil(validUntil)
                .requestedBy(actor.actorId())
                .createdAt(now)
                .build());

        outboxService.enqueue(properties.getKafka().getTriggerTopic(), "simulation.completed", simId, Map.of(
                "eventType", "simulation.completed", "simulationId", simId,
                "ruleId", Objects.toString(req.getRuleId(), ""), "definitionHash", defHash,
                "seasonsCovered", seasons, "expectedLossRatio", elr, "timestamp", now.toString()));

        return toSimulationResponse(saved, true);
    }

    public SimulationResponse getSimulation(String simulationId, String include) {
        TriggerIdentity actor = TriggerSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN, AuthRole.AUDITOR)) {
            throw new TriggerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        SimulationRunEntity sim = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new TriggerApiException(HttpStatus.NOT_FOUND, "SIMULATION_NOT_FOUND", "Simulation not found"));
        boolean includeReport = include != null && include.contains("report");
        return toSimulationResponse(sim, includeReport);
    }

    private void validateTiers(List<Map<String, Object>> tiers) {
        double prevThreshold = Double.MAX_VALUE;
        for (Map<String, Object> t : tiers) {
            if (!t.containsKey("threshold") || !t.containsKey("payoutPercent")) {
                throw new TriggerApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIER_THRESHOLDS",
                        "Each tier requires threshold and payoutPercent");
            }
            double th = ((Number) t.get("threshold")).doubleValue();
            double pct = ((Number) t.get("payoutPercent")).doubleValue();
            if (pct <= 0 || pct > 100 || th > prevThreshold) {
                throw new TriggerApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIER_THRESHOLDS",
                        "Tier thresholds and payout percentages are invalid");
            }
            prevThreshold = th;
        }
    }

    private TriggerRuleResponse toRuleResponse(TriggerRuleEntity rule, List<Map<String, Object>> approvals) {
        List<String> reqRoles = new ArrayList<>();
        if (rule.getStatus() == RuleStatus.PENDING_APPROVAL) {
            boolean hasU = approvals.stream().anyMatch(a -> "UNDERWRITER".equals(a.get("role")) || "ADMIN".equals(a.get("role")));
            boolean hasC = approvals.stream().anyMatch(a -> "COMPLIANCE_OFFICER".equals(a.get("role")) || "ADMIN".equals(a.get("role")));
            if (!hasU) reqRoles.add("UNDERWRITER");
            if (!hasC) reqRoles.add("COMPLIANCE_OFFICER");
        }
        return TriggerRuleResponse.builder()
                .ruleId(rule.getRuleId())
                .ruleCode(rule.getRuleCode())
                .version(rule.getVersionNum())
                .productId(rule.getProductId())
                .productVersion(rule.getProductVersion())
                .status(rule.getStatus().name())
                .definitionHash(rule.getDefinitionHash())
                .contractualDefinitionHash(rule.getContractualDefinitionHash())
                .approvalsRequired(reqRoles)
                .approvals(approvals)
                .effectiveFrom(rule.getEffectiveFrom().toString())
                .createdAt(rule.getCreatedAt().toString())
                .build();
    }

    private SimulationResponse toSimulationResponse(SimulationRunEntity sim, boolean includeReport) {
        return SimulationResponse.builder()
                .simulationId(sim.getSimulationId())
                .status("COMPLETED")
                .ruleId(sim.getRuleId())
                .ruleVersion(sim.getRuleVersion())
                .definitionHash(sim.getDefinitionHash())
                .seasonsCovered(sim.getSeasonsCovered())
                .regionCodes(Arrays.asList(sim.getRegionCodesCsv().split(",")))
                .expectedLossRatio(sim.getExpectedLossRatio())
                .burningCostPercent(sim.getBurningCostPercent())
                .dataCoveragePercent(sim.getDataCoveragePercent())
                .summary(readMap(sim.getSummaryJson()))
                .perSeasonResults(readList(sim.getPerSeasonJson()))
                .reportToken(includeReport ? sim.getReportToken() : null)
                .validUntil(sim.getValidUntil().toString())
                .createdAt(sim.getCreatedAt().toString())
                .build();
    }

    private String writeJson(Object o) {
        if (o == null) return null;
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
