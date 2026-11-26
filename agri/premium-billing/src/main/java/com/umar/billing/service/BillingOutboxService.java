package com.umar.billing.service;

import com.umar.billing.config.BillingProperties;
import com.umar.billing.entity.BillingOutboxEventEntity;
import com.umar.billing.repository.BillingOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingOutboxService {
    private final BillingOutboxEventRepository outboxRepository;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final BillingProperties properties;
    private final ObjectMapper objectMapper;

    public void enqueue(String topic, String eventType, String aggregateId, Map<String, Object> payload) {
        try {
            outboxRepository.save(BillingOutboxEventEntity.builder()
                    .eventId(UUID.randomUUID())
                    .topic(topic)
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payload(objectMapper.writeValueAsString(payload))
                    .published(false)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to enqueue outbox event {} for {}", eventType, aggregateId, e);
        }
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void flush() {
        KafkaTemplate<String, String> kt = kafkaTemplateProvider.getIfAvailable();
        if (!properties.getKafka().isEnabled() || kt == null) return;
        List<BillingOutboxEventEntity> pending = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
        for (BillingOutboxEventEntity evt : pending) {
            kt.send(evt.getTopic(), evt.getAggregateId(), evt.getPayload());
            evt.setPublished(true);
            outboxRepository.save(evt);
        }
    }
}
