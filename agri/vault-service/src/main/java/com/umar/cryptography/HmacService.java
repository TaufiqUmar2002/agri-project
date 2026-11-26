package com.umar.cryptography;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

/**
 * HMAC-SHA256 signing and constant-time verification inside the vault boundary.
 */
@Service
public class HmacService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    public byte[] sign(byte[] keyBytes, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(keyBytes, HMAC_SHA256));
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new SecurityException("HMAC signing failed", e);
        }
    }

    public boolean verify(byte[] keyBytes, byte[] payload, byte[] signature) {
        if (signature == null) {
            return false;
        }
        byte[] expected = sign(keyBytes, payload);
        return MessageDigest.isEqual(expected, signature);
    }
}
