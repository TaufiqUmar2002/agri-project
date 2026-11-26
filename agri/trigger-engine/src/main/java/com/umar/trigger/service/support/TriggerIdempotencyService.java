package com.umar.trigger.service.support;

import com.umar.trigger.domain.TriggerIdempotencyEntity;
import com.umar.trigger.exception.TriggerApiException;
import com.umar.trigger.repository.TriggerIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TriggerIdempotencyService {
    private final TriggerIdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> findReplay(String key, String operation, Object req, Class<T> type) {
        if (key == null || key.isBlank()) return Optional.empty();
        String composite = operation + ":" + key;
        Optional<TriggerIdempotencyEntity> existing = repository.findById(composite);
        if (existing.isEmpty()) return Optional.empty();
        String reqHash = sha256(req);
        if (!existing.get().getRequestHash().equals(reqHash)) {
            throw new TriggerApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key reused with different request payload");
        }
        try {
            return Optional.of(objectMapper.readValue(existing.get().getResponseBody(), type));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize idempotent response", e);
        }
    }

    public void save(String key, String operation, Object req, int status, Object response) {
        if (key == null || key.isBlank()) return;
        try {
            repository.save(TriggerIdempotencyEntity.builder()
                    .idempotencyKey(operation + ":" + key)
                    .operation(operation)
                    .requestHash(sha256(req))
                    .responseStatus(status)
                    .responseBody(objectMapper.writeValueAsString(response))
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception ignored) {}
    }

    private String sha256(Object o) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = objectMapper.writeValueAsString(o).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return String.valueOf(o.hashCode());
        }
    }
}
