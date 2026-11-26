package com.umar.notification.kafka;

import com.umar.notification.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationKafkaConsumer {

    private final NotificationDeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
            "platform.policy.events",
            "platform.claims.events",
            "platform.payout.events"
    }, groupId = "${spring.kafka.consumer.group-id:notification-service-cg}")
    public void onDomainEvent(String message) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            String eventId = envelope.get("eventId") != null ? String.valueOf(envelope.get("eventId")) : null;
            String eventType = String.valueOf(envelope.getOrDefault("eventType", "policy.activated"));
            String topic = String.valueOf(envelope.getOrDefault("topic", "platform.domain.events"));
            deliveryService.handleDomainEvent(eventId, topic, eventType, envelope);
        } catch (Exception ignored) {
        }
    }
}
