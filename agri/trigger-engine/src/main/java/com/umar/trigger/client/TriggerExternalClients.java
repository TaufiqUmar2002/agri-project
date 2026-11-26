package com.umar.trigger.client;

import com.umar.enums.auth.DelegationScope;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TriggerExternalClients {

    @Getter @Setter
    private volatile boolean vaultAvailable = true;
    @Getter @Setter
    private volatile boolean archiveAvailable = true;
    @Getter @Setter
    private volatile boolean forceCorroborationConflict = false;
    @Getter @Setter
    private volatile double defaultIndexValue = 42.0; // e.g., rainfall mm (< threshold = breach)

    private final Map<String, String> productContractHashes = new ConcurrentHashMap<>();
    private final Map<String, String> vaultEvidenceBundles = new ConcurrentHashMap<>();
    private final Map<String, Double> regionRainfallOverride = new ConcurrentHashMap<>();
    private final Map<String, Integer> regionArchiveSeasons = new ConcurrentHashMap<>();
    private final Set<String> activeEvaluations = ConcurrentHashMap.newKeySet();

    public void registerProductContractHash(String productId, int version, String hash) {
        productContractHashes.put(productId + ":" + version, hash);
    }

    public Optional<String> getProductContractHash(String productId, int version) {
        return Optional.ofNullable(productContractHashes.get(productId + ":" + version));
    }

    public void setRegionRainfall(String regionCode, double value) {
        regionRainfallOverride.put(regionCode, value);
    }

    public double fetchRegionIndexValue(String regionCode) {
        return regionRainfallOverride.getOrDefault(regionCode, defaultIndexValue);
    }

    public void setRegionArchiveSeasons(String regionCode, int seasons) {
        regionArchiveSeasons.put(regionCode, seasons);
    }

    public int getAvailableArchiveSeasons(List<String> regions) {
        int min = 20;
        for (String r : regions) {
            min = Math.min(min, regionArchiveSeasons.getOrDefault(r, 15));
        }
        return min;
    }

    public String storeEvidenceBundle(String decisionId, String jsonPayload) {
        if (!vaultAvailable) return null;
        String token = "vt_ev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        vaultEvidenceBundles.put(token, jsonPayload);
        return token;
    }

    public String getEvidenceBundle(String token) {
        return vaultEvidenceBundles.get(token);
    }

    public boolean tryAcquireEvalLock(String ruleId, String windowId) {
        return activeEvaluations.add(ruleId + ":" + windowId);
    }

    public void releaseEvalLock(String ruleId, String windowId) {
        activeEvaluations.remove(ruleId + ":" + windowId);
    }

    public boolean validateDelegation(UUID delegationId, UUID agentId, UUID farmerId, DelegationScope scope) {
        return delegationId != null && agentId != null && farmerId != null && scope == DelegationScope.POLICY_ASSIST;
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
