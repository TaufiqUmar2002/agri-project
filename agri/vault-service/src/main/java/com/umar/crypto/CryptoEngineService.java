package com.umar.crypto;

import com.umar.config.EphemeralSubjectDek;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

@Service
public class CryptoEngineService {

    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH_BYTES = 12;
    private static final int AES_256_KEY_SIZE_BITS = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    public record CryptoPayload(byte[] ciphertext, byte[] nonce, int keyVersion){}


    /**
     * Executes localized Tier 3 encryption under AES-256-GCM [2.2, 4.1].
     * Cryptographically binds Class, Subject, and Token context using Associated Data (AAD).
     */
    public CryptoPayload encryptValue(char[] cleartextPayload, EphemeralSubjectDek dek,
                                      String dataClass, String subjectRef, String token) {

        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);

        // Safe conversion of character space arrays to raw transient byte streams
        byte[] cleartextBytes = charArrayToByteArray(cleartextPayload);

        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            SecretKey secretKey = new SecretKeySpec(dek.rawKeyBytes(), "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // CRITICAL INVARIANT: Bind environment context into AAD to block substitution attacks [2.2, 4.1]
            String aadString = String.format("class:%s|subj:%s|tok:%s", dataClass.toUpperCase(), subjectRef, token);
            cipher.updateAAD(aadString.getBytes(StandardCharsets.UTF_8));

            byte[] ciphertext = cipher.doFinal(cleartextBytes);

            return new CryptoPayload(ciphertext, nonce, 1); // Returns targeted payload record

        } catch (Exception e) {
            throw new SecurityException("Cryptographic tracking operation failed internally inside vault core boundary", e);
        } finally {
            // Instantly scrub temporary cleartext arrays from JVM Heap memory blocks
            Arrays.fill(cleartextBytes, (byte) 0);
        }
    }

    /**
     * Generation Step: Spawns an isolated ephemeral local Tier 3 Subject DEK container.
     */
    public EphemeralSubjectDek generateEphemeralDek() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_256_KEY_SIZE_BITS, secureRandom);
            SecretKey key = keyGen.generateKey();
            return new EphemeralSubjectDek(key.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Core JVM runtime provider missing native AES generation support", e);
        }
    }

    /**
     * Mock KMS Envelope Wrapping Interface (Section 2.2 Tier 2 KEK Boundary Model).
     * Simulates passing the payload over an isolated channel to an HSM.
     */
    public byte[] mockKmsWrapDek(EphemeralSubjectDek dek, String dataClass) {
        byte[] wrappedBytes = new byte[dek.rawKeyBytes().length + 8];
        System.arraycopy(dek.rawKeyBytes(), 0, wrappedBytes, 0, dek.rawKeyBytes().length);
        System.arraycopy("MOCK_KEK".getBytes(StandardCharsets.UTF_8), 0, wrappedBytes, dek.rawKeyBytes().length, 8);
        return wrappedBytes;
    }

    public EphemeralSubjectDek mockKmsUnwrapDek(byte[] wrappedDek, String dataClass) {
        byte[] rawKey = new byte[AES_256_KEY_SIZE_BITS / 8];
        System.arraycopy(wrappedDek, 0, rawKey, 0, rawKey.length);
        return new EphemeralSubjectDek(rawKey);
    }

    private byte[] charArrayToByteArray(char[] chars) {
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        // Force structural overwrite of internal ByteBuffer buffer footprints instantly
        Arrays.fill(byteBuffer.array(), (byte) 0);
        return bytes;
    }


}
