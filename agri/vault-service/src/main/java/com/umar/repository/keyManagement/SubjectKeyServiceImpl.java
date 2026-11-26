package com.umar.repository.keyManagement;

import com.umar.config.EphemeralSubjectDek;
import com.umar.crypto.CryptoEngineService;
import com.umar.entity.keyManagement.KeyVersion;
import com.umar.entity.keyManagement.SubjectKey;
import com.umar.exception.VaultApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Manages Subject Data Encryption Keys (DEKs) per (subjectRef, retentionGroup).
 * Destroying a SubjectKey (crypto-shredding) renders every token and document
 * encrypted under it permanently unrecoverable.
 */
@Service
@RequiredArgsConstructor
public class SubjectKeyServiceImpl implements SubjectKeyService {

    private final SubjectKeyRepository subjectKeyRepository;
    private final KeyManagementService keyManagementService;
    private final CryptoEngineService cryptoEngineService;
    private final ErasureRegistryRepository erasureRegistryRepository;

    @Override
    @Transactional
    public DataKeyMaterial getOrCreateActiveKey(String subjectRef, String dataClass, String retentionGroup) {
        String group = normalizeGroup(retentionGroup);
        String subjectHash = sha256Hex(subjectRef);
        if (erasureRegistryRepository.existsBySubjectHashAndGroupAndShreddedAtIsNotNull(subjectHash, group)) {
            throw VaultApiException.gone("SUBJECT_ERASED", "Subject key destroyed for retention group " + group);
        }

        SubjectKey subjectKey = subjectKeyRepository.findBySubjectRefAndRetentionGroup(subjectRef, group)
                .orElseGet(() -> createSubjectKey(subjectRef, group));

        if ("SHREDDED".equalsIgnoreCase(subjectKey.getState()) || subjectKey.getWrappedKey() == null) {
            throw VaultApiException.gone("SUBJECT_ERASED", "Subject key destroyed for retention group " + group);
        }

        byte[] wrapped = keyManagementService.decodeBase64(subjectKey.getWrappedKey());
        byte[] raw = keyManagementService.unwrapUnderClassKek(wrapped, group, subjectKey.getClassKekVersion());
        SecretKey key = keyManagementService.toAesKey(raw);
        Arrays.fill(raw, (byte) 0);
        return new DataKeyMaterial(key, subjectKey.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public DataKeyMaterial getKeyByVersion(UUID subjectKeyId) {
        SubjectKey subjectKey = subjectKeyRepository.findById(subjectKeyId)
                .orElseThrow(() -> VaultApiException.gone("SUBJECT_ERASED", "Subject key not found"));

        if ("SHREDDED".equalsIgnoreCase(subjectKey.getState()) || subjectKey.getWrappedKey() == null) {
            throw VaultApiException.gone("SUBJECT_ERASED", "Subject key destroyed");
        }

        String group = normalizeGroup(subjectKey.getRetentionGroup());
        byte[] wrapped = keyManagementService.decodeBase64(subjectKey.getWrappedKey());
        byte[] raw = keyManagementService.unwrapUnderClassKek(wrapped, group, subjectKey.getClassKekVersion());
        SecretKey key = keyManagementService.toAesKey(raw);
        Arrays.fill(raw, (byte) 0);
        return new DataKeyMaterial(key, subjectKey.getId());
    }

    @Override
    @Transactional
    public void shredSubjectKey(String subjectRef, String retentionGroup) {
        String group = normalizeGroup(retentionGroup);
        subjectKeyRepository.findBySubjectRefAndRetentionGroup(subjectRef, group).ifPresent(sk -> {
            sk.setState("SHREDDED");
            sk.setWrappedKey(null);
            sk.setShreddedAt(Instant.now());
            subjectKeyRepository.save(sk);
        });
    }

    @Transactional(readOnly = true)
    public boolean isSubjectGroupShredded(String subjectRef, String retentionGroup) {
        String group = normalizeGroup(retentionGroup);
        if (erasureRegistryRepository.existsBySubjectHashAndGroupAndShreddedAtIsNotNull(sha256Hex(subjectRef), group)) {
            return true;
        }
        return subjectKeyRepository.findBySubjectRefAndRetentionGroup(subjectRef, group)
                .map(sk -> "SHREDDED".equalsIgnoreCase(sk.getState()) || sk.getWrappedKey() == null)
                .orElse(false);
    }

    @Transactional
    public int rewrapAllForClassKek(String retentionGroup, String newClassKekVersion) {
        String group = normalizeGroup(retentionGroup);
        List<SubjectKey> keys = subjectKeyRepository.findAll().stream()
                .filter(sk -> group.equalsIgnoreCase(sk.getRetentionGroup()) && "ACTIVE".equalsIgnoreCase(sk.getState()))
                .toList();
        int updated = 0;
        for (SubjectKey sk : keys) {
            if (sk.getWrappedKey() == null) continue;
            byte[] oldWrapped = keyManagementService.decodeBase64(sk.getWrappedKey());
            byte[] raw = keyManagementService.unwrapUnderClassKek(oldWrapped, group, sk.getClassKekVersion());
            try {
                byte[] newWrapped = keyManagementService.wrapUnderClassKek(raw, group, newClassKekVersion);
                sk.setWrappedKey(keyManagementService.encodeBase64(newWrapped));
                sk.setClassKekVersion(newClassKekVersion);
                subjectKeyRepository.save(sk);
                updated++;
            } finally {
                Arrays.fill(raw, (byte) 0);
            }
        }
        return updated;
    }

    @Transactional
    public void rotateSubjectDek(String subjectRef, String retentionGroup) {
        String group = normalizeGroup(retentionGroup);
        SubjectKey sk = subjectKeyRepository.findBySubjectRefAndRetentionGroup(subjectRef, group).orElse(null);
        if (sk == null || !"ACTIVE".equalsIgnoreCase(sk.getState())) {
            return;
        }
        KeyVersion classKek = keyManagementService.ensureActiveClassKek(group);
        try (EphemeralSubjectDek newDek = cryptoEngineService.generateEphemeralDek()) {
            byte[] wrapped = keyManagementService.wrapUnderClassKek(newDek.rawKeyBytes(), group, classKek.getVersion());
            sk.setWrappedKey(keyManagementService.encodeBase64(wrapped));
            sk.setClassKekVersion(classKek.getVersion());
            subjectKeyRepository.save(sk);
        }
    }

    private SubjectKey createSubjectKey(String subjectRef, String retentionGroup) {
        keyManagementService.ensureActiveRootKey();
        KeyVersion classKek = keyManagementService.ensureActiveClassKek(retentionGroup);
        try (EphemeralSubjectDek dek = cryptoEngineService.generateEphemeralDek()) {
            byte[] wrapped = keyManagementService.wrapUnderClassKek(
                    dek.rawKeyBytes(), retentionGroup, classKek.getVersion());
            SubjectKey sk = new SubjectKey();
            sk.setId(UUID.randomUUID());
            sk.setSubjectRef(subjectRef);
            sk.setRetentionGroup(retentionGroup);
            sk.setWrappedKey(keyManagementService.encodeBase64(wrapped));
            sk.setClassKekVersion(classKek.getVersion());
            sk.setState("ACTIVE");
            sk.setCreatedAt(Instant.now());
            return subjectKeyRepository.save(sk);
        }
    }

    private String normalizeGroup(String retentionGroup) {
        return (retentionGroup == null || retentionGroup.isBlank()) ? "ERASABLE" : retentionGroup.trim().toUpperCase();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 hashing failed", e);
        }
    }
}
