package com.umar.integration.client;

import com.umar.integration.error.HubException;
import com.umar.integration.util.CryptoUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LocalDataVaultClient implements DataVaultClient {

    private final Map<String, byte[]> keys = new ConcurrentHashMap<>();
    private final Map<String, VaultSecret> secrets = new ConcurrentHashMap<>();
    private final Map<String, VaultDocument> documents = new ConcurrentHashMap<>();
    private final Map<String, byte[]> partnerHashKeys = new ConcurrentHashMap<>();
    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private final AtomicBoolean unavailable = new AtomicBoolean(false);

    public void setUnavailable(boolean state) {
        unavailable.set(state);
    }

    public void seedDocument(String token, String dataClass, String content) {
        documents.put(token, new VaultDocument(token, dataClass, CryptoUtils.sha256Hex(content), content));
    }

    public void seedSecret(String secretRef, String ownerId, String value) {
        secrets.put(secretRef, new VaultSecret(secretRef, ownerId, value));
    }

    private void checkAvailable() {
        if (unavailable.get()) {
            throw new HubException(HttpStatus.SERVICE_UNAVAILABLE, "VAULT_UNAVAILABLE",
                    "Unable to reach Data Vault right now");
        }
    }

    @Override
    public GeneratedKey generateWebhookSigningKey(String subscriptionId, String partnerId) {
        checkAvailable();
        byte[] rawKey = CryptoUtils.randomBytes(32);
        String keyId = "wk_" + CryptoUtils.randomHex(6);
        keys.put(keyId, rawKey);
        String secret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey);
        return new GeneratedKey(keyId, secret, Instant.now());
    }

    @Override
    public String signPayload(String keyId, String timestamp, String rawBody) {
        checkAvailable();
        byte[] key = keys.get(keyId);
        if (key == null) {
            // Deterministic fallback if key was created in a prior run without persistent vault
            key = CryptoUtils.sha256Hex("fallback:" + keyId).getBytes(StandardCharsets.UTF_8);
            keys.putIfAbsent(keyId, key);
        }
        return CryptoUtils.hmacSha256Hex(key, timestamp + "." + rawBody);
    }

    @Override
    public boolean verifySecretOwned(String secretRef, String ownerId, String dataClass, String purpose) {
        checkAvailable();
        if (secretRef == null || secretRef.isBlank()) {
            return false;
        }
        VaultSecret secret = secrets.get(secretRef);
        if (secret != null) {
            return secret.ownerId() == null || secret.ownerId().equals(ownerId) || "PLATFORM".equals(ownerId);
        }
        // Accept well-formed vault secret tokens (vs_*) in dev/test when not explicitly seeded to another owner
        return secretRef.startsWith("vs_") || secretRef.startsWith("sec_");
    }

    @Override
    public Map<String, String> injectSecrets(List<String> secretRefs, String ownerId) {
        checkAvailable();
        if (secretRefs == null || secretRefs.isEmpty()) {
            return Map.of();
        }
        Map<String, String> injected = new LinkedHashMap<>();
        for (String ref : secretRefs) {
            VaultSecret secret = secrets.get(ref);
            injected.put(ref, secret != null ? secret.value() : "vault-resolved-" + ref);
        }
        return injected;
    }

    @Override
    public String storeDocument(String dataClass, String purpose, String ownerId, String content, String contentType) {
        checkAvailable();
        String token = "vt_doc_" + CryptoUtils.randomHex(6);
        documents.put(token, new VaultDocument(token, dataClass, CryptoUtils.sha256Hex(content), content));
        return token;
    }

    @Override
    public Map<String, String> verifyDocuments(List<String> documentTokens, String purpose) {
        checkAvailable();
        if (documentTokens == null || documentTokens.isEmpty()) {
            return Map.of();
        }
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String token : documentTokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            VaultDocument doc = documents.get(token);
            if (doc != null) {
                hashes.put(token, doc.sha256());
            } else if (token.startsWith("vt_doc_")) {
                hashes.put(token, CryptoUtils.sha256Hex("verified:" + token));
            }
        }
        return hashes;
    }

    @Override
    public void enableTokenization(String connectorId, List<String> piiFields) {
        checkAvailable();
    }

    @Override
    public String tokenize(String dataClass, String value, String subjectRef, String purpose) {
        checkAvailable();
        if (value == null) return null;
        String key = dataClass + ":" + value;
        return tokens.computeIfAbsent(key, k -> "vt_" + dataClass.toLowerCase(Locale.ROOT) + "_" + CryptoUtils.randomHex(6));
    }

    @Override
    public String partnerScopedHash(String partnerId, String internalId) {
        if (internalId == null) return null;
        byte[] partnerKey = partnerHashKeys.computeIfAbsent(partnerId,
                p -> CryptoUtils.sha256Hex("partner-key:" + p).getBytes(StandardCharsets.UTF_8));
        return "p_" + CryptoUtils.hmacSha256Hex(partnerKey, internalId).substring(0, 16);
    }

    private record VaultSecret(String ref, String ownerId, String value) {}
    private record VaultDocument(String token, String dataClass, String sha256, String content) {}
}
