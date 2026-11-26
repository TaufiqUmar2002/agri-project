package com.umar.integration.service.support;

import com.umar.integration.domain.entity.IdempotencyRecordEntity;
import com.umar.integration.error.HubException;
import com.umar.integration.repository.IdempotencyRecordRepository;
import com.umar.integration.util.CryptoUtils;
import com.umar.integration.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final JsonUtils jsonUtils;

    public <T> Optional<T> findExisting(String operation, String idempotencyKey, Object requestPayload, Class<T> responseType) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        Optional<IdempotencyRecordEntity> existing = repository.findByOperationAndIdempotencyKey(operation, idempotencyKey);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyRecordEntity record = existing.get();
        if (record.getExpiresAt().isBefore(Instant.now())) {
            repository.delete(record);
            return Optional.empty();
        }
        String requestHash = CryptoUtils.sha256Hex(jsonUtils.toJson(requestPayload));
        if (!record.getRequestHash().equals(requestHash)) {
            throw HubException.conflict("IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key was already used with a different request payload");
        }
        return Optional.of(jsonUtils.fromJson(record.getResponseBodyJson(), responseType));
    }

    @Transactional
    public void save(String operation, String idempotencyKey, String actorId,
                     Object requestPayload, int responseStatus, Object responseBody, Duration ttl) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        if (repository.findByOperationAndIdempotencyKey(operation, idempotencyKey).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        repository.save(IdempotencyRecordEntity.builder()
                .id("idm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .operation(operation)
                .idempotencyKey(idempotencyKey)
                .actorId(actorId)
                .requestHash(CryptoUtils.sha256Hex(jsonUtils.toJson(requestPayload)))
                .responseStatus(responseStatus)
                .responseBodyJson(jsonUtils.toJson(responseBody))
                .createdAt(now)
                .expiresAt(now.plus(ttl))
                .build());
    }
}
