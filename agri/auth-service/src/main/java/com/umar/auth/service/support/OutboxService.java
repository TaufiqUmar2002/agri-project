package com.umar.auth.service.support;

import com.umar.auth.config.AuthProperties;
import com.umar.auth.domain.entity.AuthOutboxEventEntity;
import com.umar.auth.repository.AuthOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final AuthOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final AuthProperties properties;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    public void enqueue(String topic, String eventType, String aggregateId, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            AuthOutboxEventEntity event = AuthOutboxEventEntity.builder()
                    .eventId(UUID.randomUUID())
                    .topic(topic)
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payloadJson(json)
                    .status("PENDING")
                    .createdAt(Instant.now())
                    .build();
            outboxRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event " + eventType, e);
        }
    }

    @Scheduled(fixedDelayString = "${auth.kafka.outbox-delay-ms:5000}")
    public void flushOutbox() {
        if (!properties.getKafka().isOutboxEnabled() || !properties.getKafka().isEnabled()) {
            return;
        }
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return;
        }
        List<AuthOutboxEventEntity> pending = outboxRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING");
        for (AuthOutboxEventEntity ev : pending) {
            try {
                kafkaTemplate.send(ev.getTopic(), ev.getAggregateId(), ev.getPayloadJson());
                ev.setStatus("PUBLISHED");
                ev.setPublishedAt(Instant.now());
                outboxRepository.save(ev);
            } catch (Exception ex) {
                log.warn("Failed publishing auth outbox event {}: {}", ev.getEventId(), ex.getMessage());
                break;
            }
        }
    }
}
