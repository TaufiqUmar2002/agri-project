package com.umar.audit;


import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class AuditHashService {
    public String hash(String previousHash, String canonicalEvent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(HexFormat.of().parseHex(previousHash));
            digest.update((byte) 0);
            digest.update(canonicalEvent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Audit hash calculation failed", e);
        }
    }

    public String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
