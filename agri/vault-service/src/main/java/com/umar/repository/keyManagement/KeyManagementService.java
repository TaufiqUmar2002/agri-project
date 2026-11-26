package com.umar.repository.keyManagement;

import com.umar.config.EphemeralSubjectDek;
import com.umar.crypto.CryptoEngineService;
import com.umar.entity.keyManagement.KeyVersion;
import com.umar.enums.vault.LEVEL;
import com.umar.enums.vault.State;
import com.umar.keymanagement.KeyManagementProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the Vault Key Hierarchy (section 2.2):
 * ROOT (HSM/KMS) -> CLASS_KEK (per data class) -> SUBJECT_DEK (per subject & retention group)
 * -> INDEX (blind index HMAC) & SIGNING keys.
 */
@Service
public class KeyManagementService {

    private final KeyVersionRepository keyVersionRepository;
    private final CryptoEngineService cryptoEngineService;
    private final KeyManagementProvider keyManagementProvider;

    public KeyManagementService(
            KeyVersionRepository keyVersionRepository,
            CryptoEngineService cryptoEngineService,
            @Autowired(required = false) KeyManagementProvider keyManagementProvider) {
        this.keyVersionRepository = keyVersionRepository;
        this.cryptoEngineService = cryptoEngineService;
        this.keyManagementProvider = keyManagementProvider;
    }

    @Transactional
    public KeyVersion ensureActiveClassKek(String scopeKey) {
        String normalized = normalizeScope(scopeKey);
        return keyVersionRepository
                .findFirstByLevelAndScopeKeyAndStateOrderByCreatedAtDesc(LEVEL.CLASS_KEK, normalized, State.ACTIVE)
                .orElseGet(() -> createKeyVersion(LEVEL.CLASS_KEK, normalized, 1));
    }

    @Transactional
    public KeyVersion ensureActiveRootKey() {
        return keyVersionRepository
                .findFirstByLevelAndScopeKeyAndStateOrderByCreatedAtDesc(LEVEL.ROOT, "ROOT", State.ACTIVE)
                .orElseGet(() -> createKeyVersion(LEVEL.ROOT, "ROOT", 1));
    }

    @Transactional
    public KeyVersion rotateKeyVersion(LEVEL level, String scopeKey, boolean revokeImmediately) {
        String normalized = normalizeScope(scopeKey);
        List<KeyVersion> existing = keyVersionRepository.findByLevelAndScopeKey(level, normalized);
        int maxVersion = existing.stream()
                .mapToInt(kv -> parseVersion(kv.getVersion()))
                .max()
                .orElse(0);

        State oldTargetState = revokeImmediately ? State.RETIRED : State.RETIRING;
        Instant now = Instant.now();
        for (KeyVersion kv : existing) {
            if (kv.getState() == State.ACTIVE) {
                kv.setState(oldTargetState);
                if (revokeImmediately) {
                    kv.setRetiredAt(now);
                }
                keyVersionRepository.save(kv);
            }
        }
        return createKeyVersion(level, normalized, maxVersion + 1);
    }

    @Transactional
    public void retireOldVersions(LEVEL level, String scopeKey) {
        String normalized = normalizeScope(scopeKey);
        Instant now = Instant.now();
        for (KeyVersion kv : keyVersionRepository.findByLevelAndScopeKey(level, normalized)) {
            if (kv.getState() == State.RETIRING) {
                kv.setState(State.RETIRED);
                kv.setRetiredAt(now);
                keyVersionRepository.save(kv);
            }
        }
    }

    public int getCurrentVersionNumber(LEVEL level, String scopeKey) {
        return keyVersionRepository
                .findFirstByLevelAndScopeKeyAndStateOrderByCreatedAtDesc(level, normalizeScope(scopeKey), State.ACTIVE)
                .map(kv -> parseVersion(kv.getVersion()))
                .orElse(1);
    }

    public Optional<KeyVersion> findActive(LEVEL level, String scopeKey) {
        return keyVersionRepository.findFirstByLevelAndScopeKeyAndStateOrderByCreatedAtDesc(
                level, normalizeScope(scopeKey), State.ACTIVE);
    }

    /**
     * Wraps a Subject DEK under the active Class KEK (derived inside the KMS/vault boundary).
     */
    public byte[] wrapUnderClassKek(byte[] rawSubjectDek, String classKekScope, String classKekVersion) {
        byte[] kekBytes = deriveClassKekBytes(classKekScope, classKekVersion);
        try (EphemeralSubjectDek subjectDek = new EphemeralSubjectDek(rawSubjectDek.clone())) {
            return cryptoEngineService.wrapWithKey(subjectDek.rawKeyBytes(), kekBytes,
                    "class-kek:" + classKekScope + ":v" + classKekVersion);
        } finally {
            java.util.Arrays.fill(kekBytes, (byte) 0);
        }
    }

    /**
     * Unwraps a Subject DEK using its recorded Class KEK version.
     */
    public byte[] unwrapUnderClassKek(byte[] wrappedSubjectDek, String classKekScope, String classKekVersion) {
        byte[] kekBytes = deriveClassKekBytes(classKekScope, classKekVersion);
        try {
            return cryptoEngineService.unwrapWithKey(wrappedSubjectDek, kekBytes,
                    "class-kek:" + classKekScope + ":v" + classKekVersion);
        } finally {
            java.util.Arrays.fill(kekBytes, (byte) 0);
        }
    }

    public String providerName() {
        return keyManagementProvider != null ? keyManagementProvider.providerName() : "LOCAL_MASTER_KDF";
    }

    private KeyVersion createKeyVersion(LEVEL level, String scopeKey, int versionNum) {
        KeyVersion kv = new KeyVersion();
        kv.setId(UUID.randomUUID());
        kv.setLevel(level);
        kv.setScopeKey(scopeKey);
        kv.setVersion(String.valueOf(versionNum));
        kv.setState(State.ACTIVE);
        kv.setHsmRef("hsm://" + level.name().toLowerCase() + "/" + scopeKey.toLowerCase() + "/v" + versionNum);
        kv.setCreatedAt(Instant.now());
        kv.setActivatedAt(Instant.now());
        return keyVersionRepository.save(kv);
    }

    private byte[] deriveClassKekBytes(String classKekScope, String classKekVersion) {
        try {
            byte[] root = cryptoEngineService.copyMasterKeyBytes();
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                sha256.update(root);
                sha256.update(("|" + normalizeScope(classKekScope) + "|v" + classKekVersion)
                        .getBytes(StandardCharsets.UTF_8));
                return sha256.digest();
            } finally {
                java.util.Arrays.fill(root, (byte) 0);
            }
        } catch (Exception e) {
            throw new SecurityException("Failed to derive class KEK", e);
        }
    }

    public SecretKey toAesKey(byte[] rawKey) {
        return new SecretKeySpec(rawKey, "AES");
    }

    public String encodeBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public byte[] decodeBase64(String b64) {
        return Base64.getDecoder().decode(b64);
    }

    private String normalizeScope(String scopeKey) {
        return (scopeKey == null || scopeKey.isBlank()) ? "DEFAULT" : scopeKey.trim().toUpperCase();
    }

    private int parseVersion(String version) {
        try {
            return Integer.parseInt(version);
        } catch (Exception e) {
            return 1;
        }
    }
}
