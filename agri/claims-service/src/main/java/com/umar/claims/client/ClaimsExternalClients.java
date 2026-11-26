package com.umar.claims.client;

import com.umar.enums.auth.StepUpActionClass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClaimsExternalClients {

    public record PolicyTermsView(boolean active, long sumInsuredMinor, long paidToDateMinor,
                                  String termsHash, String currency, String farmerId, String plotId, String regionCode) {}

    public record TriggerStatusView(boolean finalBreach, double payoutPercent, String tierId) {}

    @Getter @Setter
    private volatile boolean triggerServiceAvailable = true;
    @Getter @Setter
    private volatile boolean policyServiceAvailable = true;
    @Getter @Setter
    private volatile boolean vaultAvailable = true;
    @Getter @Setter
    private volatile boolean authAvailable = true;

    private final Map<String, PolicyTermsView> policyTerms = new ConcurrentHashMap<>();
    private final Map<String, TriggerStatusView> triggerDecisions = new ConcurrentHashMap<>();
    private final Map<String, String> farmerConsent = new ConcurrentHashMap<>(); // FARMER_CONFIRMED_AUTO vs WITHDRAWN
    private final Set<String> fraudFlaggedPolicies = ConcurrentHashMap.newKeySet();
    private final Set<String> frozenAccounts = ConcurrentHashMap.newKeySet();
    private final Set<String> agentSoldPolicies = ConcurrentHashMap.newKeySet(); // key: agentId:policyId
    private final Map<String, String> validStepUpTokens = new ConcurrentHashMap<>(); // token -> farmerId:actionClass

    public void registerPolicyTerms(String policyId, PolicyTermsView terms) {
        policyTerms.put(policyId, terms);
    }

    public PolicyTermsView fetchPolicyTerms(String policyId) {
        return policyTerms.getOrDefault(policyId, new PolicyTermsView(
                true, 500_000L, 0L, sha256(policyId + ":terms"), "KES", "frm_default", "plt_default", "KE-NYERI"));
    }

    public void registerTriggerDecision(String decisionId, TriggerStatusView view) {
        triggerDecisions.put(decisionId, view);
    }

    public TriggerStatusView fetchTriggerStatus(String decisionId) {
        return triggerDecisions.getOrDefault(decisionId, new TriggerStatusView(true, 60.0, "T1"));
    }

    public void setAutomatedPayoutConsent(String farmerId, boolean granted) {
        farmerConsent.put(farmerId, granted ? "GRANTED" : "WITHDRAWN");
    }

    public boolean hasAutomatedPayoutConsent(String farmerId) {
        return !"WITHDRAWN".equals(farmerConsent.getOrDefault(farmerId, "GRANTED"));
    }

    public void flagFraud(String policyId, boolean flagged) {
        if (flagged) fraudFlaggedPolicies.add(policyId); else fraudFlaggedPolicies.remove(policyId);
    }

    public boolean isFraudFlagged(String policyId) {
        return fraudFlaggedPolicies.contains(policyId);
    }

    public void freezeAccount(String farmerId, boolean frozen) {
        if (frozen) frozenAccounts.add(farmerId); else frozenAccounts.remove(farmerId);
    }

    public boolean isAccountFrozen(String farmerId) {
        return frozenAccounts.contains(farmerId);
    }

    public void markAgentSoldPolicy(String agentId, String policyId) {
        agentSoldPolicies.add(agentId + ":" + policyId);
    }

    public boolean isConflictOfInterest(String actorId, String policyId, String farmerId) {
        return actorId.equals(farmerId) || agentSoldPolicies.contains(actorId + ":" + policyId);
    }

    public void registerStepUpToken(String token, String farmerId, StepUpActionClass actionClass) {
        validStepUpTokens.put(token, farmerId + ":" + actionClass.name());
    }

    public boolean validateAndConsumeStepUp(String token, String farmerId, StepUpActionClass actionClass) {
        if (!authAvailable || token == null || token.isBlank()) return false;
        String val = validStepUpTokens.remove(token);
        return (farmerId + ":" + actionClass.name()).equals(val);
    }

    public boolean verifyPhotoTokens(List<String> tokens) {
        return vaultAvailable;
    }

    public String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
