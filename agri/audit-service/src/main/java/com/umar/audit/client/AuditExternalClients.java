package com.umar.audit.client;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class AuditExternalClients {

    private final Map<String, String> subjectPseudonyms = new ConcurrentHashMap<>();
    private final Set<String> erasedSubjects = ConcurrentHashMap.newKeySet();
    private final Map<String, String> vaultEvidenceHashes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> activeVaultHolds = new ConcurrentHashMap<>();
    private final Map<String, String> wormWrittenHeads = new ConcurrentHashMap<>();

    public AuditExternalClients() {
        subjectPseudonyms.put("frm_001", "subj_tok_001");
        subjectPseudonyms.put("frm_002", "subj_tok_002");
        vaultEvidenceHashes.put("evd_001", sha256("evidence-bundle-001"));
    }

    public Optional<String> deriveSubjectToken(String farmerId) {
        if (erasedSubjects.contains(farmerId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(subjectPseudonyms.get(farmerId));
    }

    public void markSubjectErased(String farmerId) {
        erasedSubjects.add(farmerId);
    }

    public boolean verifyEvidenceBundleHash(String bundleRef, String expectedHash) {
        String actual = vaultEvidenceHashes.get(bundleRef);
        if (actual == null) return true; // default valid if unseeded ref
        return actual.equals(expectedHash);
    }

    public void syncHoldToVault(String holdId, Map<String, Object> scope, boolean active) {
        if (active) {
            activeVaultHolds.put(holdId, scope);
        } else {
            activeVaultHolds.remove(holdId);
        }
    }

    public boolean isVaultHoldActive(String holdId) {
        return activeVaultHolds.containsKey(holdId);
    }

    public void writeCheckpointToWorm(String chainId, String headHash) {
        wormWrittenHeads.put(chainId, headHash);
    }

    public Optional<String> readWormHeadHash(String chainId) {
        return Optional.ofNullable(wormWrittenHeads.get(chainId));
    }

    public String signDigest(String secret, String digest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(digest.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
