package com.umar.blindindex;


import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class BlindIndexCalculator {

    private final BlindIndexKeyProvider keyProvider;

    public BlindIndexCalculator(
            BlindIndexKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public byte[] calculate(
            UUID keyVersionId,
            String canonicalValue) {

        if (canonicalValue == null || canonicalValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Canonical value cannot be empty");
        }

        byte[] input = canonicalValue.getBytes(StandardCharsets.UTF_8);

        byte[] result = keyProvider.calculateHmac(
                keyVersionId,
                input);

        if (result == null || result.length != 32) {
            throw new IllegalStateException(
                    "HMAC-SHA256 provider must return exactly 32 bytes");
        }

        return result.clone();
    }
}
