package com.umar.idempotency;


import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
public class IdempotencyCleanupJob {

    private final IdempotencyRepository repository;

    public IdempotencyCleanupJob(IdempotencyRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${vault.idempotency.cleanup-cron}")
    @Transactional
    public void deleteExpiredRecords() {

        int deleted = repository.deleteExpired(Instant.now());
        log.debug("Idempotency cleanup removed {} expired records", deleted);
        // Log only count and execution metadata.
        // Never log request payloads, fingerprints or sensitive values.
    }
}
