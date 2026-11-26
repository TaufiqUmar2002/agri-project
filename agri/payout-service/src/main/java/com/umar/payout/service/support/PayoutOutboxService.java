package com.umar.payout.service.support;

import com.umar.payout.domain.PayoutOutboxEventEntity;
import com.umar.payout.repository.PayoutOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutOutboxService {
    private final PayoutOutboxEventRepository outboxRepository;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    public void enqueue(String topic, String eventType, String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            PayoutOutboxEventEntity ev = PayoutOutboxEventEntity.builder()
                    .eventId(UUID.randomUUID())
                    .topic(topic)
                    .eventType(eventType)
                    .aggregateKey(key)
                    .payloadJson(json)
                    .published(false)
                    .createdAt(Instant.now())
                    .build();
            KafkaTemplate<String, String> tpl = kafkaTemplateProvider.getIfAvailable();
            if (tpl != null) {
                try { tpl.send(topic, key, json); ev.setPublished(true); } catch (Exception ignored) {}
            }
            outboxRepository.save(ev);
        } catch (Exception ignored) {}
    }
}
