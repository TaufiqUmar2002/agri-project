package com.umar.crypto;

import com.umar.config.EphemeralSubjectDek;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

@Service
public class CryptoEngineService {

    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH_BYTES = 12;
    private static final int AES_256_KEY_SIZE_BITS = 256;
    private static final int WRAP_FORMAT_VERSION = 1;
    private static final byte[] DEK_WRAP_AAD = "vault-dek-wrap".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${vault.master-key:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}")
    private String masterKeyBase64;

    public record CryptoPayload(byte[] ciphertext, byte[] nonce, int keyVersion) {}

    public CryptoPayload encryptValue(char[] cleartextPayload, EphemeralSubjectDek dek,
                                      String dataClass, String subjectRef, String token) {
        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] cleartextBytes = charArrayToByteArray(cleartextPayload);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            SecretKey secretKey = new SecretKeySpec(dek.rawKeyBytes(), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            String aadString = String.format("class:%s|subj:%s|tok:%s", dataClass.toUpperCase(), subjectRef, token);
            cipher.updateAAD(aadString.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(cleartextBytes);
            return new CryptoPayload(ciphertext, nonce, 1);
        } catch (Exception e) {
            throw new SecurityException("Cryptographic tracking operation failed internally inside vault core boundary", e);
        } finally {
            Arrays.fill(cleartextBytes, (byte) 0);
        }
    }

    public String decryptValue(byte[] ciphertext, byte[] nonce, EphemeralSubjectDek dek,
                               String dataClass, String subjectRef, String token) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            SecretKey secretKey = new SecretKeySpec(dek.rawKeyBytes(), "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            String aadString = String.format("class:%s|subj:%s|tok:%s", dataClass.toUpperCase(), subjectRef, token);
            cipher.updateAAD(aadString.getBytes(StandardCharsets.UTF_8));
            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("Cryptographic decryption operation failed inside vault core boundary", e);
        }
    }

    public byte[] wrapDek(EphemeralSubjectDek dek) {
        try {
            byte[] masterKey = decodeMasterKey();
            byte[] wrapped = wrapWithKey(dek.rawKeyBytes(), masterKey, new String(DEK_WRAP_AAD, StandardCharsets.UTF_8));
            Arrays.fill(masterKey, (byte) 0);
            return wrapped;
        } catch (Exception e) {
            throw new SecurityException("Failed to wrap data encryption key", e);
        }
    }

    public EphemeralSubjectDek unwrapDek(byte[] wrappedDek) {
        try {
            byte[] masterKey = decodeMasterKey();
            byte[] rawKey = unwrapWithKey(wrappedDek, masterKey, new String(DEK_WRAP_AAD, StandardCharsets.UTF_8));
            Arrays.fill(masterKey, (byte) 0);
            return new EphemeralSubjectDek(rawKey);
        } catch (Exception e) {
            throw new SecurityException("Failed to unwrap data encryption key", e);
        }
    }

    public byte[] wrapDekWithSubjectKey(EphemeralSubjectDek dek, SecretKey subjectDek, UUID subjectKeyId) {
        if (subjectDek == null || subjectKeyId == null) {
            return wrapDek(dek);
        }
        return wrapWithKey(dek.rawKeyBytes(), subjectDek.getEncoded(), "subj:" + subjectKeyId);
    }

    public EphemeralSubjectDek unwrapDekWithSubjectKey(byte[] wrappedDek, SecretKey subjectDek, UUID subjectKeyId) {
        if (subjectDek == null || subjectKeyId == null) {
            return unwrapDek(wrappedDek);
        }
        return new EphemeralSubjectDek(unwrapWithKey(wrappedDek, subjectDek.getEncoded(), "subj:" + subjectKeyId));
    }

    public byte[] copyMasterKeyBytes() {
        return decodeMasterKey();
    }

    public byte[] wrapWithKey(byte[] targetKeyBytes, byte[] wrappingKeyBytes, String aadContext) {
        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(wrappingKeyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            cipher.updateAAD((aadContext != null ? aadContext : "vault-key-wrap").getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(targetKeyBytes);
            ByteBuffer output = ByteBuffer.allocate(1 + GCM_NONCE_LENGTH_BYTES + ciphertext.length);
            output.put((byte) WRAP_FORMAT_VERSION);
            output.put(nonce);
            output.put(ciphertext);
            return output.array();
        } catch (Exception e) {
            throw new SecurityException("Failed to wrap key under wrapping key", e);
        }
    }

    public byte[] unwrapWithKey(byte[] wrappedKey, byte[] wrappingKeyBytes, String aadContext) {
        try {
            if (wrappedKey == null || wrappedKey.length < 1 + GCM_NONCE_LENGTH_BYTES + 16) {
                throw new IllegalArgumentException("Invalid wrapped key");
            }
            ByteBuffer buffer = ByteBuffer.wrap(wrappedKey);
            byte version = buffer.get();
            if (version != WRAP_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported wrapped key format version");
            }
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(wrappingKeyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            cipher.updateAAD((aadContext != null ? aadContext : "vault-key-wrap").getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new SecurityException("Failed to unwrap key under wrapping key", e);
        }
    }

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

    public String encryptEnvelope(String plaintext, String dataClass) {
        if (plaintext == null || plaintext.isEmpty() || plaintext.startsWith("vlt:v1:")) {
            return plaintext;
        }
        try {
            byte[] masterKey = decodeMasterKey();
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            String cls = dataClass != null ? dataClass.toUpperCase() : "PII";
            cipher.updateAAD(("env:" + cls).getBytes(StandardCharsets.UTF_8));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Arrays.fill(masterKey, (byte) 0);
            byte[] out = new byte[nonce.length + cipherBytes.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(cipherBytes, 0, out, nonce.length, cipherBytes.length);
            return "vlt:v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception e) {
            throw new SecurityException("Envelope encryption failed inside vault-service", e);
        }
    }

    public String decryptEnvelope(String token, String dataClass) {
        if (token == null || token.isEmpty() || !token.startsWith("vlt:v1:")) {
            return token;
        }
        try {
            byte[] combined = Base64.getUrlDecoder().decode(token.substring("vlt:v1:".length()));
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            byte[] cipherBytes = new byte[combined.length - GCM_NONCE_LENGTH_BYTES];
            System.arraycopy(combined, 0, nonce, 0, GCM_NONCE_LENGTH_BYTES);
            System.arraycopy(combined, GCM_NONCE_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);
            byte[] masterKey = decodeMasterKey();
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            String cls = dataClass != null ? dataClass.toUpperCase() : "PII";
            cipher.updateAAD(("env:" + cls).getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(cipherBytes);
            Arrays.fill(masterKey, (byte) 0);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("Envelope decryption failed inside vault-service", e);
        }
    }

    private byte[] decodeMasterKey() {
        try {
            byte[] decoded = Base64.getDecoder().decode(masterKeyBase64);
            if (decoded.length == 32) {
                return decoded;
            }
            return sha256(decoded);
        } catch (Exception e) {
            return sha256(masterKeyBase64.getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private byte[] charArrayToByteArray(char[] chars) {
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0);
        return bytes;
    }
}
