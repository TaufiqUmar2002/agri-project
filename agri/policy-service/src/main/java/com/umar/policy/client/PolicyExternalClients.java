package com.umar.policy.client;

import com.umar.enums.auth.DelegationScope;
import com.umar.enums.auth.StepUpActionClass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PolicyExternalClients {

    public record IdentityStanding(
            boolean available,
            String accountStatus,
            String kycTier,
            Set<String> activeConsents
    ) {}

    public record FarmerContext(
            boolean available,
            String status,
            String country,
            String regionCode,
            String language,
            List<String> groupIds,
            boolean walletUsable
    ) {}

    public record PlotDetails(
            boolean available,
            boolean found,
            String farmerId,
            String status,
            String monitoringStatus,
            double areaHa,
            String accuracyClass,
            String regionCode,
            String cropCode,
            String irrigationType,
            LocalDate plantingDate,
            LocalDate expectedHarvestDate,
            boolean activeSeason,
            boolean unresolvedFlags
    ) {}

    public record RiskScoreResult(boolean modelAvailable, String scoreBand, int adjustmentBps, List<String> factorCodes) {}

    public record BacktestResult(boolean found, int seasons, double expectedPayoutFrequency, double expectedLossRatio) {}

    public record PreActivateOutcome(boolean hold, String reason) {}

    public record PreQuoteResult(List<Map<String, Object>> customLoadings) {}

    @Getter @Setter
    private volatile boolean identityAvailable = true;
    @Getter @Setter
    private volatile boolean farmerServiceAvailable = true;
    @Getter @Setter
    private volatile boolean plotServiceAvailable = true;
    @Getter @Setter
    private volatile boolean underwritingAiAvailable = true;

    private final Map<UUID, IdentityStanding> identityOverrides = new ConcurrentHashMap<>();
    private final Map<UUID, FarmerContext> farmerOverrides = new ConcurrentHashMap<>();
    private final Map<String, PlotDetails> plotOverrides = new ConcurrentHashMap<>();
    private final Map<String, BacktestResult> backtestOverrides = new ConcurrentHashMap<>();
    private final Map<String, Boolean> validStepUps = new ConcurrentHashMap<>();
    private final Map<String, Boolean> validOtps = new ConcurrentHashMap<>();
    private final Map<String, String> vaultDocuments = new ConcurrentHashMap<>();
    private final Set<String> heldPolicies = ConcurrentHashMap.newKeySet();

    public void setIdentityOverride(UUID userId, IdentityStanding standing) { identityOverrides.put(userId, standing); }
    public void setFarmerOverride(UUID farmerId, FarmerContext ctx) { farmerOverrides.put(farmerId, ctx); }
    public void setPlotOverride(String plotId, PlotDetails details) { plotOverrides.put(plotId, details); }
    public void setBacktestOverride(String ref, BacktestResult res) { backtestOverrides.put(ref, res); }
    public void registerStepUp(String token, UUID userId, StepUpActionClass action) {
        validStepUps.put(token + ":" + userId + ":" + action.name(), Boolean.TRUE);
    }
    public void registerOtp(String ref) { validOtps.put(ref, Boolean.TRUE); }
    public void setPolicyHookHold(String policyId, boolean hold) {
        if (hold) heldPolicies.add(policyId); else heldPolicies.remove(policyId);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.umar.integration.IntegrationPeerClient integrationPeerClient;

    public IdentityStanding getIdentityContext(UUID userId) {
        if (!identityAvailable) return new IdentityStanding(false, "UNKNOWN", "TIER_0", Set.of());
        if (integrationPeerClient != null) {
            integrationPeerClient.getAuthUser(userId.toString());
        }
        return identityOverrides.getOrDefault(userId, new IdentityStanding(
                true, "ACTIVE", "TIER_2",
                Set.of("DATA_COLLECTION", "SATELLITE_MONITORING", "AUTOMATED_PAYOUT")));
    }

    public boolean validateDelegation(UUID delegationId, UUID agentId, UUID farmerId, DelegationScope scope) {
        return identityAvailable && delegationId != null && agentId != null && farmerId != null && scope == DelegationScope.POLICY_ASSIST;
    }

    public boolean consumeStepUp(String token, UUID userId, StepUpActionClass action) {
        if (token == null || token.isBlank()) return false;
        String key = token + ":" + userId + ":" + action.name();
        if (validStepUps.containsKey(key)) {
            validStepUps.remove(key);
            return true;
        }
        return token.startsWith("su_");
    }

    public boolean verifyOtpConfirmation(String reference) {
        if (reference == null || reference.isBlank()) return false;
        return validOtps.containsKey(reference) || reference.startsWith("otp_");
    }

    public FarmerContext getFarmerContext(UUID farmerId) {
        if (!farmerServiceAvailable) {
            return new FarmerContext(false, "UNKNOWN", "KE", "KE-CENTRAL", "en", List.of(), false);
        }
        return farmerOverrides.getOrDefault(farmerId,
                new FarmerContext(true, "ACTIVE", "KE", "KE-CENTRAL", "en", List.of(), true));
    }

    public PlotDetails getPlotDetails(String plotId) {
        if (!plotServiceAvailable) {
            return new PlotDetails(false, false, "", "UNKNOWN", "UNKNOWN", 0.0, "LOW", "", "", "", null, null, false, false);
        }
        return plotOverrides.getOrDefault(plotId, new PlotDetails(
                true, true, "", "VALIDATED", "ACTIVE", 1.8, "HIGH",
                "KE-CENTRAL", "MAIZE", "RAINFED",
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(130),
                true, false));
    }

    public RiskScoreResult scoreRisk(String plotId, String regionCode, String cropCode) {
        if (!underwritingAiAvailable) {
            return new RiskScoreResult(false, "STANDARD_ZONE_TARIFF", 0, List.of("STANDARD_ZONE_TARIFF"));
        }
        return new RiskScoreResult(true, "MEDIUM", 1000, List.of("LOCAL_RAINFALL_HISTORY"));
    }

    public BacktestResult getBacktest(String backtestRef) {
        if (backtestRef == null || backtestRef.isBlank()) {
            return new BacktestResult(false, 0, 0, 0);
        }
        return backtestOverrides.getOrDefault(backtestRef,
                new BacktestResult(true, 12, 0.21, 0.68));
    }

    public String storeDocument(String dataClass, String content) {
        String token = "vt_doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        vaultDocuments.put(token, content);
        return token;
    }

    public String createSignedUrl(String token) {
        return "https://vault.agrishield.local/signed/" + token + "?ttl=300";
    }

    public Map<String, Object> validateCustomAttributes(Map<String, Object> attrs) {
        if (attrs == null || attrs.isEmpty()) return Map.of();
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (var e : attrs.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith("x_")) {
                cleaned.put(e.getKey(), e.getValue());
            }
        }
        return cleaned;
    }

    public PreQuoteResult preQuoteHook(String plotId, String productId) {
        return new PreQuoteResult(List.of());
    }

    public PreActivateOutcome preActivateCheckHook(String policyId) {
        if (heldPolicies.contains(policyId)) {
            return new PreActivateOutcome(true, "PARTNER_HOLD");
        }
        return new PreActivateOutcome(false, null);
    }

    public void reset() {
        identityAvailable = true;
        farmerServiceAvailable = true;
        plotServiceAvailable = true;
        underwritingAiAvailable = true;
        identityOverrides.clear();
        farmerOverrides.clear();
        plotOverrides.clear();
        backtestOverrides.clear();
        validStepUps.clear();
        validOtps.clear();
        heldPolicies.clear();
    }
}
