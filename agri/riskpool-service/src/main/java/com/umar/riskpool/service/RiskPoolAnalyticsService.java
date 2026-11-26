package com.umar.riskpool.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.riskpool.LedgerViewType;
import com.umar.enums.riskpool.PeriodStatus;
import com.umar.payload.request.riskpool.ConfigurePoolAlertsRequest;
import com.umar.payload.response.riskpool.*;
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
import java.util.*;

@Service
@RequiredArgsConstructor
public class RiskPoolAnalyticsService {

    private final RiskPoolRepository poolRepository;
    private final PolicyPremiumRegisterRepository registerRepository;
    private final ReinsuranceTreatyRepository treatyRepository;
    private final ClaimReserveRepository reserveRepository;
    private final JournalTransactionRepository txRepository;
    private final PoolPeriodRepository periodRepository;
    private final RiskPoolSupportService support;
    private final RiskPoolProperties properties;
    private final ObjectMapper objectMapper;

    public PoolExposureResponse getExposure(String poolId, String regionCode, Double stressPercent) {
        RiskPoolIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.UNDERWRITER, AuthRole.REINSURANCE_MANAGER,
                AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR, AuthRole.ADMIN, AuthRole.REINSURER_VIEWER)) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        RiskPoolEntity pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new RiskPoolApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found"));

        List<PolicyPremiumRegisterEntity> active = registerRepository.findByPoolId(poolId).stream()
                .filter(PolicyPremiumRegisterEntity::isInForce).toList();
        long totalTsi = active.stream().mapToLong(PolicyPremiumRegisterEntity::getSumInsuredMinor).sum();

        Map<String, List<PolicyPremiumRegisterEntity>> byRegion = new LinkedHashMap<>();
        active.forEach(p -> byRegion.computeIfAbsent(p.getRegionCode(), k -> new ArrayList<>()).add(p));

        List<Map<String, Object>> groups = new ArrayList<>();
        double maxShare = 0.0;
        for (var e : byRegion.entrySet()) {
            long tsi = e.getValue().stream().mapToLong(PolicyPremiumRegisterEntity::getSumInsuredMinor).sum();
            double share = totalTsi > 0 ? (double) tsi / totalTsi : 0.0;
            maxShare = Math.max(maxShare, share);
            boolean suppressed = e.getValue().size() < properties.getRules().getMinGroupSize();
            groups.add(Map.of("region", e.getKey(), "totalSumInsuredMinor", tsi,
                    "policies", e.getValue().size(), "shareOfPool", share, "suppressed", suppressed));
        }

        List<Map<String, Object>> scenarios = null;
        if (stressPercent != null) {
            long base = regionCode != null && byRegion.containsKey(regionCode)
                    ? byRegion.get(regionCode).stream().mapToLong(PolicyPremiumRegisterEntity::getSumInsuredMinor).sum()
                    : totalTsi;
            long grossLoss = Math.round(base * (stressPercent / 100.0) * 0.5);
            long recovery = Math.round(grossLoss * 0.25);
            scenarios = List.of(Map.of("region", Objects.toString(regionCode, "ALL"),
                    "grossLossMinor", grossLoss, "recoveryMinor", recovery, "netLossMinor", grossLoss - recovery));
        }

        return PoolExposureResponse.builder()
                .poolId(poolId).asOf(Instant.now().toString()).currency(pool.getCurrency())
                .totals(Map.of("totalSumInsuredMinor", totalTsi, "policies", active.size()))
                .groups(groups)
                .concentration(Map.of("topRegionShare", maxShare, "limit", 0.45))
                .scenarios(scenarios).generatedAt(Instant.now().toString()).build();
    }

    public BordereauxResponse getBordereaux(String poolId, String type, String periodStr, String treatyId) {
        RiskPoolIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.REINSURANCE_MANAGER, AuthRole.COMPLIANCE_OFFICER,
                AuthRole.AUDITOR, AuthRole.ADMIN, AuthRole.REINSURER_VIEWER)) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        RiskPoolEntity pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new RiskPoolApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found"));

        PoolPeriodEntity period = periodRepository.findById(poolId + ":" + periodStr).orElse(null);
        boolean closed = period != null && period.getStatus() == PeriodStatus.CLOSED;
        if (actor.hasRole(AuthRole.REINSURER_VIEWER) && !closed) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "External viewers can read closed periods only");
        }

        List<PolicyPremiumRegisterEntity> regs = registerRepository.findByPoolId(poolId);
        long gross = regs.stream().mapToLong(PolicyPremiumRegisterEntity::getPaidMinor).sum();
        long ceded = Math.round(gross * 0.25);
        long commission = Math.round(ceded * 0.20);
        long reserved = reserveRepository.findByPoolId(poolId).stream()
                .filter(r -> !r.isReleased()).mapToLong(ClaimReserveEntity::getReservedMinor).sum();

        List<Map<String, Object>> rows = regs.stream().map(r -> Map.<String, Object>of(
                "policyRef", "p_" + support.sha256(r.getPolicyId()).substring(0, 8),
                "region", r.getRegionCode(), "peril", r.getPeril(),
                "sumInsuredMinor", r.getSumInsuredMinor(), "grossPremiumMinor", r.getPaidMinor(),
                "cededPremiumMinor", Math.round(r.getPaidMinor() * 0.25))).toList();

        String hash = support.sha256(poolId + "|" + type + "|" + periodStr + "|" + gross + "|" + rows.size());
        return BordereauxResponse.builder()
                .runId("bdx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .poolId(poolId).type(type).period(periodStr).status(closed ? "FINAL" : "PROVISIONAL")
                .totals(Map.of("policies", regs.size(), "grossPremiumMinor", gross,
                        "cededPremiumMinor", ceded, "commissionMinor", commission, "reservedMinor", reserved))
                .rows(rows).contentHash(hash).generatedAt(Instant.now().toString())
                .page(0).size(100).totalElements(rows.size()).build();
    }

    @Transactional
    public PoolAlertsResponse configureAlerts(String poolId, ConfigurePoolAlertsRequest req, String ifMatch) {
        RiskPoolIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.UNDERWRITER, AuthRole.ADMIN)) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new RiskPoolApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "If-Match header is required");
        }
        RiskPoolEntity pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new RiskPoolApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found"));

        String expectedTag = "\"" + pool.getAlertVersion() + "\"";
        if (!expectedTag.equals(ifMatch) && !String.valueOf(pool.getAlertVersion()).equals(ifMatch)) {
            throw new RiskPoolApiException(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT",
                    "If-Match does not match current version", Map.of("currentVersion", pool.getAlertVersion()));
        }

        pool.setAlertsJson(writeJson(req.getAlerts()));
        pool.setAlertVersion(pool.getAlertVersion() + 1);
        poolRepository.save(pool);

        Instant now = Instant.now();
        support.enqueue(properties.getKafka().getLedgerTopic(), "pool.alerts.configured", poolId, Map.of(
                "eventType", "pool.alerts.configured", "poolId", poolId, "version", pool.getAlertVersion(),
                "changedBy", actor.actorId(), "timestamp", now.toString()));

        return PoolAlertsResponse.builder()
                .poolId(poolId).version(pool.getAlertVersion()).alerts(req.getAlerts())
                .updatedAt(now.toString()).updatedBy(actor.actorId()).build();
    }

    public LedgerViewResponse getLedger(String poolId, LedgerViewType view, boolean verify) {
        RiskPoolIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR,
                AuthRole.ADMIN, AuthRole.UNDERWRITER, AuthRole.REINSURANCE_MANAGER)) {
            throw new RiskPoolApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        RiskPoolEntity pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new RiskPoolApiException(HttpStatus.NOT_FOUND, "POOL_NOT_FOUND", "Pool not found"));

        List<JournalTransactionEntity> txs = txRepository.findByPoolIdOrderByCreatedAtAsc(poolId);
        Map<String, Long> debits = new TreeMap<>();
        Map<String, Long> credits = new TreeMap<>();
        List<Map<String, Object>> entryRows = new ArrayList<>();
        boolean chainIntact = true;
        String prevExpected = "GENESIS";

        for (JournalTransactionEntity tx : txs) {
            String recomputed = support.sha256(tx.getTransactionId() + "|" + poolId + "|" + tx.getLinesJson() + "|" + prevExpected);
            if (!recomputed.equals(tx.getEntryHash())) chainIntact = false;
            prevExpected = tx.getEntryHash();

            for (Map<String, Object> line : readList(tx.getLinesJson())) {
                String acc = Objects.toString(line.get("account"), "");
                String side = Objects.toString(line.get("side"), "DR");
                long amt = ((Number) line.getOrDefault("amountMinor", 0L)).longValue();
                if ("DR".equalsIgnoreCase(side)) debits.merge(acc, amt, Long::sum);
                else credits.merge(acc, amt, Long::sum);
                entryRows.add(Map.of("transactionId", tx.getTransactionId(), "account", acc,
                        "side", side, "amountMinor", amt, "postingDate", tx.getPostingDate().toString()));
            }
        }
        if (verify && !chainIntact) {
            throw new RiskPoolApiException(HttpStatus.CONFLICT, "INTEGRITY_BREACH", "Hash chain verification failed");
        }

        long totDr = debits.values().stream().mapToLong(Long::longValue).sum();
        long totCr = credits.values().stream().mapToLong(Long::longValue).sum();
        Set<String> allAccs = new TreeSet<>(debits.keySet());
        allAccs.addAll(credits.keySet());
        List<Map<String, Object>> tbLines = allAccs.stream()
                .map(a -> Map.<String, Object>of("account", a,
                        "debitMinor", debits.getOrDefault(a, 0L), "creditMinor", credits.getOrDefault(a, 0L)))
                .toList();

        return LedgerViewResponse.builder()
                .poolId(poolId).view(view.name()).currency(pool.getCurrency())
                .entries(view == LedgerViewType.ENTRIES ? entryRows : null)
                .trialBalance(view == LedgerViewType.TRIAL_BALANCE || view == LedgerViewType.BALANCES
                        ? Map.of("lines", tbLines, "totalDebitMinor", totDr, "totalCreditMinor", totCr, "balanced", totDr == totCr)
                        : null)
                .integrity(verify ? Map.of("intact", chainIntact, "transactionsChecked", txs.size()) : null)
                .page(0).size(100).totalElements(entryRows.size()).build();
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
}
