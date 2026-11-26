package com.umar.crypto;

import com.umar.integration.IntegrationHubFeignClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Shared encryption/decryption facade ensuring raw sensitive data is never persisted to any DB.
 * In distributed runtime (`agreeshield.integration.mode=feign`), routes encrypt/decrypt through
 * integration-service -> vault-service. In isolated local test mode, uses identical AES-256-GCM
 * Vault ciphertext envelope (`vlt:v1:...`).
 */
@Slf4j
@Service
public class VaultCryptoService {

    public static final String PREFIX = "vlt:v1:";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private static volatile VaultCryptoService INSTANCE;

    private final ObjectProvider<IntegrationHubFeignClient> hubClientProvider;
    private final SecretKey localKey;
    private final String mode;
    private final SecureRandom secureRandom = new SecureRandom();

    public VaultCryptoService(
            ObjectProvider<IntegrationHubFeignClient> hubClientProvider,
            @Value("${agreeshield.integration.mode:local}") String mode,
            @Value("${agreeshield.vault.local-secret:AgreeShieldVaultMasterSecretKey2026!}") String localSecret) {
        this.hubClientProvider = hubClientProvider;
        this.mode = mode;
        this.localKey = deriveAesKey(localSecret);
    }

    @PostConstruct
    void bindStatic() {
        INSTANCE = this;
    }

    public static VaultCryptoService instance() {
        return INSTANCE;
    }

    public String encrypt(String plaintext) {
        return encrypt(plaintext, "PII");
    }

    public String encrypt(String plaintext, String dataClass) {
        if (plaintext == null || plaintext.isEmpty() || plaintext.startsWith(PREFIX)) {
            return plaintext;
        }
        if ("feign".equalsIgnoreCase(mode)) {
            IntegrationHubFeignClient client = hubClientProvider != null ? hubClientProvider.getIfAvailable() : null;
            if (client != null) {
                try {
                    Map<String, String> res = client.vaultEncrypt(Map.of("plaintext", plaintext, "dataClass", dataClass));
                    if (res != null && res.get("ciphertext") != null) {
                        return res.get("ciphertext");
                    }
                } catch (Exception ex) {
                    log.warn("Vault encrypt via integration-service failed ({}), using local AES-GCM envelope", ex.getMessage());
                }
            }
        }
        return encryptLocalAesGcm(plaintext);
    }

    public String decrypt(String ciphertext) {
        return decrypt(ciphertext, "PII");
    }

    public String decrypt(String ciphertext, String dataClass) {
        if (ciphertext == null || ciphertext.isEmpty() || !ciphertext.startsWith(PREFIX)) {
            return ciphertext;
        }
        if ("feign".equalsIgnoreCase(mode)) {
            IntegrationHubFeignClient client = hubClientProvider != null ? hubClientProvider.getIfAvailable() : null;
            if (client != null) {
                try {
                    Map<String, String> res = client.vaultDecrypt(Map.of("ciphertext", ciphertext, "dataClass", dataClass));
                    if (res != null && res.get("plaintext") != null) {
                        return res.get("plaintext");
                    }
                } catch (Exception ex) {
                    log.debug("Vault decrypt via integration-service fallback to local envelope: {}", ex.getMessage());
                }
            }
        }
        return decryptLocalAesGcm(ciphertext);
    }

    public String encryptLocalAesGcm(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, localKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Vault envelope encryption failed", e);
        }
    }

    public String decryptLocalAesGcm(String token) {
        try {
            String raw = token.substring(PREFIX.length());
            byte[] combined = Base64.getUrlDecoder().decode(raw);
            byte[] iv = new byte[GCM_IV_LEN];
            byte[] encrypted = new byte[combined.length - GCM_IV_LEN];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LEN);
            System.arraycopy(combined, GCM_IV_LEN, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, localKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Vault envelope decryption failed", e);
        }
    }

    private static SecretKey deriveAesKey(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
