package com.umar.blindindex;

import com.umar.entity.dataProtection.BlindIndexKeyVersion;
import com.umar.repository.dataProtection.BlindIndexKeyVersionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * HMAC-SHA256 blind-index key provider. Index keys are derived deterministically
 * from the vault master key and the key-version id, so no raw index key material
 * is persisted. Version lifecycle (ACTIVE/RETIRING/RETIRED/DESTROYED) is tracked
 * in {@link BlindIndexKeyVersion}.
 */
@Component
public class BlindIndexKeyProviderImpl implements BlindIndexKeyProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_DOMAIN = "blind-index-key:";
    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_RETIRING = "RETIRING";

    private final BlindIndexKeyVersionRepository versionRepository;
    private final byte[] masterKey;

    public BlindIndexKeyProviderImpl(
            BlindIndexKeyVersionRepository versionRepository,
            @Value("${vault.master-key:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}") String masterKeyBase64) {
        this.versionRepository = versionRepository;
        this.masterKey = Base64.getDecoder().decode(masterKeyBase64);
        if (this.masterKey.length != 32) {
            throw new IllegalStateException("vault.master-key must decode to a 32-byte AES-256 key");
        }
    }

    @Override
    public byte[] calculateHmac(UUID keyVersionId, byte[] canonicalValue) {
        try {
            byte[] derivedKey = hmac(masterKey, (KEY_DOMAIN + keyVersionId).getBytes(StandardCharsets.UTF_8));
            return hmac(derivedKey, canonicalValue);
        } catch (Exception e) {
            throw new IllegalStateException("Blind-index HMAC calculation failed", e);
        }
    }

    @Override
    @Transactional
    public List<IndexKeyVersion> getLookupKeyVersions(String dataClass) {
        BlindIndexKeyVersion active = ensureActiveVersion(dataClass);
        List<IndexKeyVersion> versions = versionRepository
                .findByDataClassAndStateIn(dataClass, List.of(STATE_ACTIVE, STATE_RETIRING))
                .stream()
                .map(this::toIndexKeyVersion)
                .toList();
        return versions.isEmpty() ? List.of(toIndexKeyVersion(active)) : versions;
    }

    @Override
    @Transactional
    public IndexKeyVersion getActiveWriteKey(String dataClass) {
        BlindIndexKeyVersion active = ensureActiveVersion(dataClass);
        return toIndexKeyVersion(active);
    }

    private BlindIndexKeyVersion ensureActiveVersion(String dataClass) {
        return versionRepository
                .findFirstByDataClassAndStateOrderByVersionDesc(dataClass, STATE_ACTIVE)
                .orElseGet(() -> {
                    BlindIndexKeyVersion version = new BlindIndexKeyVersion();
                    version.setDataClass(dataClass);
                    version.setVersion(1);
                    version.setState(STATE_ACTIVE);
                    return versionRepository.saveAndFlush(version);
                });
    }

    private IndexKeyVersion toIndexKeyVersion(BlindIndexKeyVersion v) {
        KeyState state = switch (v.getState()) {
            case STATE_ACTIVE -> KeyState.ACTIVE;
            case STATE_RETIRING -> KeyState.RETIRING;
            case "RETIRED" -> KeyState.RETIRED;
            case "DESTROYED" -> KeyState.DESTROYED;
            default -> KeyState.ACTIVE;
        };
        return new IndexKeyVersion(v.getId(), v.getVersion(), state);
    }

    private byte[] hmac(byte[] key, byte[] message) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        return mac.doFinal(message);
    }
}