package com.umar.riskpool.service.support;

import com.umar.riskpool.domain.RiskPoolIdempotencyEntity;
import com.umar.riskpool.domain.RiskPoolOutboxEventEntity;
import com.umar.riskpool.exception.RiskPoolApiException;
import com.umar.riskpool.repository.RiskPoolIdempotencyRepository;
import com.umar.riskpool.repository.RiskPoolOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskPoolSupportService {

    private final RiskPoolOutboxEventRepository outboxRepository;
    private final RiskPoolIdempotencyRepository idempotencyRepository;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    public void enqueue(String topic, String eventType, String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            RiskPoolOutboxEventEntity ev = RiskPoolOutboxEventEntity.builder()
                    .eventId(UUID.randomUUID()).topic(topic).eventType(eventType)
                    .aggregateKey(key).payloadJson(json).published(false).createdAt(Instant.now()).build();
            KafkaTemplate<String, String> tpl = kafkaTemplateProvider.getIfAvailable();
            if (tpl != null) {
                try { tpl.send(topic, key, json); ev.setPublished(true); } catch (Exception ignored) {}
            }
            outboxRepository.save(ev);
        } catch (Exception ignored) {}
    }

    public <T> Optional<T> findReplay(String key, String operation, Object req, Class<T> type) {
        if (key == null || key.isBlank()) return Optional.empty();
        Optional<RiskPoolIdempotencyEntity> ex = idempotencyRepository.findById(operation + ":" + key);
        if (ex.isEmpty()) return Optional.empty();
        if (!ex.get().getRequestHash().equals(sha256Json(req))) {
            throw new RiskPoolApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency-Key conflict");
        }
        try { return Optional.of(objectMapper.readValue(ex.get().getResponseBody(), type)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public void saveIdempotency(String key, String operation, Object req, int status, Object resp) {
        if (key == null || key.isBlank()) return;
        try {
            idempotencyRepository.save(RiskPoolIdempotencyEntity.builder()
                    .idempotencyKey(operation + ":" + key).operation(operation)
                    .requestHash(sha256Json(req)).responseStatus(status)
                    .responseBody(objectMapper.writeValueAsString(resp)).createdAt(Instant.now()).build());
        } catch (Exception ignored) {}
    }

    public String sha256Json(Object o) {
        try {
            return sha256(objectMapper.writeValueAsString(o));
        } catch (Exception e) {
            return String.valueOf(o.hashCode());
        }
    }

    public String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
