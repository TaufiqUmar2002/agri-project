package com.umar.plot.service.support;

import com.umar.plot.domain.entity.PlotIdempotencyEntity;
import com.umar.plot.exception.PlotApiException;
import com.umar.plot.geo.GeoUtils;
import com.umar.plot.repository.PlotIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlotIdempotencyService {

    private final PlotIdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> findReplay(String key, String op, Object body, Class<T> type) {
        if (key == null || key.isBlank()) return Optional.empty();
        Optional<PlotIdempotencyEntity> rec = repository
                .findByIdempotencyKeyAndOperationAndExpiresAtAfter(key, op, Instant.now());
        if (rec.isEmpty()) return Optional.empty();
        String hash = hash(body);
        if (!rec.get().getRequestHash().equals(hash)) {
            throw new PlotApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key reused with different payload");
        }
        try {
            return Optional.of(objectMapper.readValue(rec.get().getResponseJson(), type));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void save(String key, String op, Object body, int status, Object response) {
        if (key == null || key.isBlank()) return;
        try {
            Instant now = Instant.now();
            repository.save(PlotIdempotencyEntity.builder()
                    .idempotencyKey(key)
                    .operation(op)
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
        try { return GeoUtils.sha256Hex(objectMapper.writeValueAsString(body)); }
        catch (Exception e) { return String.valueOf(body.hashCode()); }
    }
}
