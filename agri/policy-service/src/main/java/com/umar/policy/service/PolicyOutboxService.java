package com.umar.policy.service;

import com.umar.policy.config.PolicyProperties;
import com.umar.policy.entity.PolicyOutboxEventEntity;
import com.umar.policy.repository.PolicyOutboxEventRepository;
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
public class PolicyOutboxService {
    private final PolicyOutboxEventRepository outboxRepository;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final PolicyProperties properties;
    private final ObjectMapper objectMapper;

    public void enqueue(String topic, String eventType, String aggregateId, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(PolicyOutboxEventEntity.builder()
                    .eventId(UUID.randomUUID())
                    .topic(topic)
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payload(json)
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
        List<PolicyOutboxEventEntity> pending = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
        for (PolicyOutboxEventEntity evt : pending) {
            kt.send(evt.getTopic(), evt.getAggregateId(), evt.getPayload());
            evt.setPublished(true);
            outboxRepository.save(evt);
        }
    }
}
