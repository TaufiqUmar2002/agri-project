package com.umar.billing.service;

import com.umar.billing.entity.BillingIdempotencyEntity;
import com.umar.billing.exception.BillingApiException;
import com.umar.billing.repository.BillingIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BillingIdempotencyService {
    private final BillingIdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> findReplay(String key, String operation, Object requestBody, Class<T> type) {
        if (key == null || key.isBlank()) return Optional.empty();
        String scopedKey = operation + ":" + key.trim();
        Optional<BillingIdempotencyEntity> existing = repository.findById(scopedKey);
        if (existing.isEmpty()) return Optional.empty();
        BillingIdempotencyEntity rec = existing.get();
        if (rec.getExpiresAt().isBefore(Instant.now())) {
            repository.delete(rec);
            return Optional.empty();
        }
        String hash = hashObject(requestBody);
        if (!rec.getRequestHash().equals(hash)) {
            throw new BillingApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key reused with different request parameters");
        }
        try {
            return Optional.of(objectMapper.readValue(rec.getResponseBody(), type));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize cached idempotency response", e);
        }
    }

    public void save(String key, String operation, Object requestBody, int status, Object response) {
        if (key == null || key.isBlank()) return;
        String scopedKey = operation + ":" + key.trim();
        try {
            Instant now = Instant.now();
            repository.save(BillingIdempotencyEntity.builder()
                    .idempotencyKey(scopedKey)
                    .operation(operation)
                    .requestHash(hashObject(requestBody))
                    .httpStatus(status)
                    .responseBody(objectMapper.writeValueAsString(response))
                    .createdAt(now)
                    .expiresAt(now.plus(Duration.ofHours(24)))
                    .build());
        } catch (Exception ignored) {
        }
    }

    public String hashObject(Object obj) {
        try {
            String json = obj instanceof String s ? s : objectMapper.writeValueAsString(obj);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "hash_err";
        }
    }
}
