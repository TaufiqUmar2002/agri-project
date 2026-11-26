package com.umar.iot.service.support;

import com.umar.iot.domain.IotIdempotencyEntity;
import com.umar.iot.domain.IotOutboxEventEntity;
import com.umar.iot.exception.IotApiException;
import com.umar.iot.repository.IotIdempotencyRepository;
import com.umar.iot.repository.IotOutboxEventRepository;
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
public class IotSupportService {
    private final IotOutboxEventRepository outboxRepository;
    private final IotIdempotencyRepository idempotencyRepository;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    public void enqueue(String topic, String eventType, String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            IotOutboxEventEntity ev = IotOutboxEventEntity.builder()
                    .eventId(UUID.randomUUID()).topic(topic).eventType(eventType)
                    .aggregateKey(key).payloadJson(json).published(false).createdAt(Instant.now()).build();
            KafkaTemplate<String, String> tpl = kafkaTemplateProvider.getIfAvailable();
            if (tpl != null) {
                try { tpl.send(topic, key, json); ev.setPublished(true); } catch (Exception ignored) {}
            }
            outboxRepository.save(ev);
        } catch (Exception ignored) {}
    }

    public <T> Optional<T> findReplay(String key, String op, Object req, Class<T> type) {
        if (key == null || key.isBlank()) return Optional.empty();
        Optional<IotIdempotencyEntity> ex = idempotencyRepository.findById(op + ":" + key);
        if (ex.isEmpty()) return Optional.empty();
        if (!ex.get().getRequestHash().equals(sha256Json(req))) {
            throw new IotApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency-Key conflict");
        }
        try { return Optional.of(objectMapper.readValue(ex.get().getResponseBody(), type)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public void saveIdempotency(String key, String op, Object req, int status, Object resp) {
        if (key == null || key.isBlank()) return;
        try {
            idempotencyRepository.save(IotIdempotencyEntity.builder()
                    .idempotencyKey(op + ":" + key).operation(op)
                    .requestHash(sha256Json(req)).responseStatus(status)
                    .responseBody(objectMapper.writeValueAsString(resp)).createdAt(Instant.now()).build());
        } catch (Exception ignored) {}
    }

    public String sha256Json(Object o) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(objectMapper.writeValueAsString(o).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { return String.valueOf(o.hashCode()); }
    }
}
