package com.umar.auth.service.support;

import com.umar.auth.domain.entity.AuthIdempotencyEntity;
import com.umar.auth.exception.AuthApiException;
import com.umar.auth.repository.AuthIdempotencyRepository;
import com.umar.auth.security.CryptoHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final AuthIdempotencyRepository repository;
    private final CryptoHashService cryptoHashService;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> findReplay(String key, String operation, Object requestBody, Class<T> type) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthIdempotencyEntity> existing = repository
                .findByIdempotencyKeyAndOperationAndExpiresAtAfter(key, operation, Instant.now());
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        String reqHash = hashRequest(requestBody);
        AuthIdempotencyEntity rec = existing.get();
        if (!rec.getRequestHash().equals(reqHash)) {
            throw new AuthApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key reused with different request payload");
        }
        try {
            return Optional.of(objectMapper.readValue(rec.getResponseJson(), type));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize idempotent response", e);
        }
    }

    public void save(String key, String operation, Object requestBody, int httpStatus, Object response) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            Instant now = Instant.now();
            repository.save(AuthIdempotencyEntity.builder()
                    .idempotencyKey(key)
                    .operation(operation)
                    .requestHash(hashRequest(requestBody))
                    .httpStatus(httpStatus)
                    .responseJson(objectMapper.writeValueAsString(response))
                    .createdAt(now)
                    .expiresAt(now.plus(24, ChronoUnit.HOURS))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist idempotency record", e);
        }
    }

    private String hashRequest(Object requestBody) {
        try {
            return cryptoHashService.hmacSha256(objectMapper.writeValueAsString(requestBody));
        } catch (Exception e) {
            return cryptoHashService.hmacSha256(String.valueOf(requestBody));
        }
    }
}
