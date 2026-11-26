package com.umar.farmer.service.support;

import com.umar.farmer.client.DataVaultClient;
import com.umar.farmer.domain.entity.FarmerIdempotencyEntity;
import com.umar.farmer.exception.FarmerApiException;
import com.umar.farmer.repository.FarmerIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FarmerIdempotencyService {

    private final FarmerIdempotencyRepository repository;
    private final DataVaultClient vaultClient;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> findReplay(String key, String operation, Object body, Class<T> type) {
        if (key == null || key.isBlank()) return Optional.empty();
        Optional<FarmerIdempotencyEntity> rec = repository
                .findByIdempotencyKeyAndOperationAndExpiresAtAfter(key, operation, Instant.now());
        if (rec.isEmpty()) return Optional.empty();
        String hash = hash(body);
        if (!rec.get().getRequestHash().equals(hash)) {
            throw new FarmerApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key reused with different payload");
        }
        try {
            return Optional.of(objectMapper.readValue(rec.get().getResponseJson(), type));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void save(String key, String operation, Object body, int status, Object response) {
        if (key == null || key.isBlank()) return;
        try {
            Instant now = Instant.now();
            repository.save(FarmerIdempotencyEntity.builder()
                    .idempotencyKey(key)
                    .operation(operation)
                    .requestHash(hash(body))
                    .httpStatus(status)
                    .responseJson(objectMapper.writeValueAsString(response))
                    .createdAt(now)
                    .expiresAt(now.plus(24, ChronoUnit.HOURS))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String hash(Object body) {
        try {
            return vaultClient.hash(objectMapper.writeValueAsString(body), "IDEMPOTENCY");
        } catch (Exception e) {
            return String.valueOf(body.hashCode());
        }
    }
}
