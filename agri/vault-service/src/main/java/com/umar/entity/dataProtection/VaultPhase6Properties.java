package com.umar.entity.dataProtection;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault")
public record VaultPhase6Properties(
        Idempotency idempotency,
        BlindIndex blindIndex) {

    public record Idempotency(
            int retentionHours,
            String cleanupCron,
            int processingLeaseSeconds) {
    }

    public record BlindIndex(
            String algorithm,
            int batchSize) {
    }
}
