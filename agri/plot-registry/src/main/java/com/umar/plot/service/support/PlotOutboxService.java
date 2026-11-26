package com.umar.plot.service.support;

import com.umar.plot.config.PlotProperties;
import com.umar.plot.domain.entity.PlotOutboxEventEntity;
import com.umar.plot.repository.PlotOutboxEventRepository;
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
public class PlotOutboxService {

    private final PlotOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final PlotProperties properties;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    public void enqueue(String topic, String eventType, String aggregateId, Map<String, Object> payload) {
        try {
            repository.save(PlotOutboxEventEntity.builder()
                    .eventId(UUID.randomUUID())
                    .topic(topic)
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .status("PENDING")
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue plot event " + eventType, e);
        }
    }

    @Scheduled(fixedDelayString = "${plot.kafka.outbox-delay-ms:5000}")
    public void flush() {
        if (!properties.getKafka().isOutboxEnabled() || !properties.getKafka().isEnabled()) return;
        KafkaTemplate<String, String> kt = kafkaTemplateProvider.getIfAvailable();
        if (kt == null) return;
        List<PlotOutboxEventEntity> pending = repository.findTop100ByStatusOrderByCreatedAtAsc("PENDING");
        for (PlotOutboxEventEntity ev : pending) {
            try {
                kt.send(ev.getTopic(), ev.getAggregateId(), ev.getPayloadJson());
                ev.setStatus("PUBLISHED");
                ev.setPublishedAt(Instant.now());
                repository.save(ev);
            } catch (Exception ex) {
                log.warn("Plot outbox flush failed: {}", ex.getMessage());
                break;
            }
        }
    }
}
