package com.umar.integration.messaging;

import com.umar.integration.service.WebhookService;
import com.umar.integration.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hub.kafka.enabled", havingValue = "true")
public class DomainEventKafkaListener {

    private final WebhookService webhookService;
    private final JsonUtils jsonUtils;

    @KafkaListener(
            topics = {
                    "platform.policy.events",
                    "platform.billing.events",
                    "platform.claim.events",
                    "platform.trigger.events",
                    "platform.farmer.events",
                    "platform.plot.events",
                    "platform.product.events"
            },
            groupId = "hub-dispatch-cg"
    )
    public void onDomainEvent(ConsumerRecord<String, String> record) {
        Map<String, Object> envelope = jsonUtils.toMap(record.value());
        if (envelope == null || envelope.isEmpty()) {
            return;
        }
        WebhookService.DispatchSummary summary = webhookService.processDomainEvent(
                envelope, record.topic(), record.partition(), record.offset());
        log.debug("INT-03 processed event on {} [partition={}, offset={}]: {}",
                record.topic(), record.partition(), record.offset(), summary);
    }

    @KafkaListener(topics = "identity.data-rights.events", groupId = "hub-dispatch-cg")
    public void onDataRightsEvent(ConsumerRecord<String, String> record) {
        Map<String, Object> envelope = jsonUtils.toMap(record.value());
        String eventType = Objects.toString(envelope.get("eventType"), "");
        if ("data.request.received".equals(eventType)) {
            String requestId = Objects.toString(envelope.get("requestId"), record.key());
            webhookService.handleDataRequestReceived(requestId);
        }
    }
}
