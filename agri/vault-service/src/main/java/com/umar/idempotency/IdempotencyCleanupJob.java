//package com.umar.idempotency;
//
//
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Instant;
//
//@Component
//public class IdempotencyCleanupJob {
//
//    private final IdempotencyRepository repository;
//
//    public IdempotencyCleanupJob(IdempotencyRepository repository) {
//        this.repository = repository;
//    }
//
//    @Scheduled(cron = "${vault.idempotency.cleanup-cron}")
//    @Transactional
//    public void deleteExpiredRecords() {
//
//        int deleted = repository.deleteExpired(Instant.now());
//
//        // Log only count and execution metadata.
//        // Never log request payloads, fingerprints or sensitive values.
//    }
//}
