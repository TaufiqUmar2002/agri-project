package com.umar.audit.kafka;

import com.umar.audit.service.AuditIntakeAndKeeperService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audit.kafka.enabled", havingValue = "true")
public class AuditKafkaConsumer {

    private final AuditIntakeAndKeeperService intakeService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
            "platform.policy.events",
            "platform.claims.events",
            "platform.payout.events",
            "platform.trigger.events",
            "platform.iot.events",
            "platform.notification.events",
            "platform.audit.intake"
    }, groupId = "${spring.kafka.consumer.group-id:audit-service-cg}")
    public void onDomainEvent(String message) {
        try {
            Map<String, Object> env = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            String eventId = env.get("eventId") != null ? String.valueOf(env.get("eventId")) : null;
            String topic = String.valueOf(env.getOrDefault("topic", "platform.audit.intake"));
            String producer = String.valueOf(env.getOrDefault("producer", "unknown"));
            String eventType = String.valueOf(env.getOrDefault("eventType", "unknown.event"));
            intakeService.ingestDomainEvent(eventId, topic, producer, eventType, env);
        } catch (Exception ignored) {
        }
    }
}
