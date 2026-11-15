package com.umar.crypto;


import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

@Service
public class AesGcmCryptoService implements CryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int AES_256_KEY_LENGTH_BYTES = 32;
    private final SecureRandom secureRandom;
    private final AadBuilder aadBuilder;

    public AesGcmCryptoService(AadBuilder aadBuilder) {
        this.aadBuilder = aadBuilder;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public EncryptedPayload encrypt(String plaintext, SecretKey dataEncryptionKey, EncryptionContext context) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }
        validateKey(dataEncryptionKey);
        try {
            // Generate a fresh nonce for every encryption
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, dataEncryptionKey, parameterSpec);
            // Authenticate metadata
            byte[] aad = aadBuilder.build(context);
            cipher.updateAAD(aad);
            // Encrypt plaintext and append GCM authentication tag
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new EncryptedPayload(ciphertext, nonce);
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("AES-GCM encryption failed", exception);
        }
    }

    @Override
    public String decrypt(EncryptedPayload payload, SecretKey dataEncryptionKey, EncryptionContext context) {
        if (payload == null) {
            throw new IllegalArgumentException("Encrypted payload is required");
        }
        validateKey(dataEncryptionKey);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, payload.nonce());
            cipher.init(Cipher.DECRYPT_MODE, dataEncryptionKey, parameterSpec);
            // Must match the AAD used during encryption
            byte[] aad = aadBuilder.build(context);
            cipher.updateAAD(aad);
            // Authentication is verified during doFinal()
            byte[] plaintext = cipher.doFinal(payload.ciphertext());
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);

        } catch (AEADBadTagException exception) {
            throw new CryptoException("Authentication failed: ciphertext or AAD was modified", exception);
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("AES-GCM decryption failed", exception);
        }
    }

    private void validateKey(SecretKey key) {
        if (key == null) {
            throw new CryptoException("Data encryption key is missing");
        }
        if (!"AES".equalsIgnoreCase(key.getAlgorithm())) {
            throw new CryptoException("Invalid encryption key algorithm");
        }
        /*
         * Development-friendly validation.
         *
         * HSM/KMS keys may be non-exportable, in which case
         * getEncoded() returns null. Do not reject such keys
         * solely because their bytes are not exportable.
         */
        byte[] encoded = key.getEncoded();

        if (encoded != null && encoded.length != AES_256_KEY_LENGTH_BYTES) {
            throw new CryptoException("AES-256 requires a 32-byte key");
        }
    }
}
