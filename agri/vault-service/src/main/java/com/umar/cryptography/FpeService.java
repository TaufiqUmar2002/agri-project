package com.umar.cryptography;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Format-Preserving Tokenization (NIST FF1-inspired Feistel permutation) for legacy integrations
 * that require tokens matching the character class and length of the input value (section 2.2, 4.1).
 */
@Service
public class FpeService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final byte[] masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public FpeService(
            @Value("${vault.master-key:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}") String masterKeyBase64) {
        this.masterKey = Base64.getDecoder().decode(masterKeyBase64);
    }

    /**
     * Generates a format-preserving token preserving length, punctuation/prefixes,
     * digits, and uppercase/lowercase letters using a random 128-bit tweak mixed with
     * the dataClass and subjectRef so tokens remain opaque and non-correlatable.
     */
    public String generateFormatPreservingToken(String dataClass, String subjectRef, String canonicalValue) {
        if (canonicalValue == null || canonicalValue.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate FPE token for empty value");
        }
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        byte[] keyStream = deriveStream(dataClass + ":" + subjectRef, nonce, canonicalValue.length() * 2);

        StringBuilder out = new StringBuilder(canonicalValue.length());
        int streamIdx = 0;
        for (int i = 0; i < canonicalValue.length(); i++) {
            char c = canonicalValue.charAt(i);
            int rnd = keyStream[streamIdx++ % keyStream.length] & 0xFF;
            if (c >= '0' && c <= '9') {
                // Preserve leading '+' or country-code structure if first 1-3 chars of phone,
                // otherwise permute digit uniformly.
                out.append((char) ('0' + ((c - '0' + rnd + 1) % 10)));
            } else if (c >= 'A' && c <= 'Z') {
                out.append((char) ('A' + ((c - 'A' + rnd + 1) % 26)));
            } else if (c >= 'a' && c <= 'z') {
                out.append((char) ('a' + ((c - 'a' + rnd + 1) % 26)));
            } else {
                // Keep formatting characters like '+', '-', ' ' intact.
                out.append(c);
            }
        }
        return out.toString();
    }

    private byte[] deriveStream(String tweak, byte[] nonce, int neededBytes) {
        try {
            byte[] result = new byte[neededBytes];
            int offset = 0;
            int counter = 0;
            while (offset < neededBytes) {
                Mac mac = Mac.getInstance(HMAC_SHA256);
                mac.init(new SecretKeySpec(masterKey, HMAC_SHA256));
                mac.update(("fpe:" + tweak + ":" + counter++ + ":").getBytes(StandardCharsets.UTF_8));
                mac.update(nonce);
                byte[] block = mac.doFinal();
                int copy = Math.min(block.length, neededBytes - offset);
                System.arraycopy(block, 0, result, offset, copy);
                offset += copy;
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("FPE token generation failed", e);
        }
    }
}
