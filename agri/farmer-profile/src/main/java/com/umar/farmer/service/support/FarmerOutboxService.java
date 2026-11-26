package com.umar.farmer.service.support;

import com.umar.farmer.config.FarmerProperties;
import com.umar.farmer.domain.entity.FarmerOutboxEventEntity;
import com.umar.farmer.repository.FarmerOutboxEventRepository;
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
public class FarmerOutboxService {

    private final FarmerOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final FarmerProperties properties;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    public void enqueue(String topic, String eventType, String aggregateId, Map<String, Object> payload) {
        try {
            repository.save(FarmerOutboxEventEntity.builder()
                    .eventId(UUID.randomUUID())
                    .topic(topic)
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .status("PENDING")
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue event " + eventType, e);
        }
    }

    @Scheduled(fixedDelayString = "${farmer.kafka.outbox-delay-ms:5000}")
    public void flush() {
        if (!properties.getKafka().isOutboxEnabled() || !properties.getKafka().isEnabled()) return;
        KafkaTemplate<String, String> kt = kafkaTemplateProvider.getIfAvailable();
        if (kt == null) return;
        List<FarmerOutboxEventEntity> pending = repository.findTop100ByStatusOrderByCreatedAtAsc("PENDING");
        for (FarmerOutboxEventEntity ev : pending) {
            try {
                kt.send(ev.getTopic(), ev.getAggregateId(), ev.getPayloadJson());
                ev.setStatus("PUBLISHED");
                ev.setPublishedAt(Instant.now());
                repository.save(ev);
            } catch (Exception ex) {
                log.warn("Outbox flush failed: {}", ex.getMessage());
                break;
            }
        }
    }
}
