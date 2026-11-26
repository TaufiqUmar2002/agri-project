package com.umar.integration.service.support;

import com.umar.integration.config.HubProperties;
import com.umar.integration.domain.entity.OutboxEventEntity;
import com.umar.integration.repository.OutboxEventRepository;
import com.umar.integration.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final HubProperties properties;
    private final JsonUtils jsonUtils;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    @Transactional
    public void publishIntegrationEvent(String eventType, String key, Map<String, Object> fields) {
        publishToTopic(properties.getKafka().getIntegrationTopic(), eventType, key, fields);
    }

    @Transactional
    public void publishToTopic(String topic, String eventType, String key, Map<String, Object> fields) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        envelope.put("eventType", eventType);
        envelope.put("timestamp", Instant.now().toString());
        if (fields != null) {
            envelope.putAll(fields);
        }

        OutboxEventEntity entity = OutboxEventEntity.builder()
                .id("obx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .topic(topic)
                .eventKey(key != null ? key : "hub")
                .eventType(eventType)
                .payloadJson(jsonUtils.toJson(envelope))
                .published(false)
                .createdAt(Instant.now())
                .build();
        outboxRepository.save(entity);

        if (properties.getKafka().isOutboxEnabled()) {
            trySend(entity);
        }
    }

    @Scheduled(fixedDelayString = "${hub.kafka.outbox-delay-ms:5000}")
    @Transactional
    public void flushOutbox() {
        if (!properties.getKafka().isOutboxEnabled()) {
            return;
        }
        List<OutboxEventEntity> pending = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEventEntity event : pending) {
            trySend(event);
        }
    }

    private void trySend(OutboxEventEntity entity) {
        KafkaTemplate<String, String> template = kafkaTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            template.send(entity.getTopic(), entity.getEventKey(), entity.getPayloadJson());
            entity.setPublished(true);
            entity.setPublishedAt(Instant.now());
            outboxRepository.save(entity);
        } catch (Exception e) {
            log.warn("Outbox delivery deferred for {} ({}): {}", entity.getEventType(), entity.getId(), e.getMessage());
        }
    }
}
