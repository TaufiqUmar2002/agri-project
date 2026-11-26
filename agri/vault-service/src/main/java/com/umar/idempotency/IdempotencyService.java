package com.umar.idempotency;


import com.umar.entity.dataProtection.IdempotencyRecord;
import com.umar.entity.dataProtection.VaultPhase6Properties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Service
public class IdempotencyService {

    public static final String TOKENIZE_OPERATION = "TOKENIZE";

    private final IdempotencyRepository repository;
    private final RequestFingerprintService fingerprintService;
    private final ObjectMapper objectMapper;
    private final VaultPhase6Properties properties;

    public IdempotencyService(
            IdempotencyRepository repository,
            RequestFingerprintService fingerprintService,
            ObjectMapper objectMapper,
            VaultPhase6Properties properties) {

        this.repository = repository;
        this.fingerprintService = fingerprintService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public IdempotencyClaim claim(
            String callerService,
            String operation,
            UUID idempotencyKey,
            Object request,
            String processingOwner) {

        byte[] fingerprint = fingerprintService.createFingerprint(
                callerService, operation, request);

        Instant now = Instant.now();

        UUID recordId = UUID.randomUUID();

        Instant expiresAt = now.plusSeconds(
                properties.idempotency().retentionHours() * 3600L);

        Instant leaseUntil = now.plusSeconds(
                properties.idempotency().processingLeaseSeconds());

        int inserted = repository.insertIfAbsent(
                recordId,
                callerService,
                operation,
                idempotencyKey,
                fingerprint,
                processingOwner,
                leaseUntil,
                now,
                expiresAt);

        if (inserted == 1) {
            return IdempotencyClaim.newRequest(recordId);
        }

        IdempotencyRecord existing = repository.findByScopeAndKey(
                callerService,
                operation,
                idempotencyKey
        ).orElseThrow(() ->
                new IllegalStateException(
                        "Idempotency record disappeared after conflict"));

        if (!Arrays.equals(
                fingerprint,
                existing.getRequestFingerprint())) {

            return IdempotencyClaim.conflict(existing.getId());
        }

        if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
            return IdempotencyClaim.replay(
                    existing.getId(),
                    existing.getResponsePayload(),
                    existing.getHttpStatus());
        }

        if (existing.getStatus() == IdempotencyStatus.FAILED) {
            return IdempotencyClaim.inProgress(existing.getId());
        }

        if (existing.getLeaseUntil() != null
                && existing.getLeaseUntil().isBefore(now)) {

            int acquired = repository.acquireExpiredLease(
                    existing.getId(),
                    processingOwner,
                    leaseUntil,
                    now);

            if (acquired == 1) {
                return IdempotencyClaim.newRequest(existing.getId());
            }
        }

        return IdempotencyClaim.inProgress(existing.getId());
    }

    @Transactional
    public void complete(
            UUID recordId,
            String processingOwner,
            Object response,
            int httpStatus) {

        try {
            String payload = objectMapper.writeValueAsString(response);

            int updated = repository.complete(
                    recordId,
                    processingOwner,
                    payload,
                    httpStatus,
                    Instant.now());

            if (updated != 1) {
                throw new IllegalStateException(
                        "Idempotency completion ownership lost");
            }

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to serialize idempotent response", ex);
        }
    }

    @Transactional
    public void markFailed(UUID recordId, String processingOwner) {

        repository.markFailed(
                recordId,
                processingOwner,
                Instant.now());
    }

    public <T> T deserializeReplay(
            String responsePayload,
            Class<T> responseType) {

        try {
            return objectMapper.readValue(responsePayload, responseType);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to deserialize stored idempotent response", ex);
        }
    }
}
