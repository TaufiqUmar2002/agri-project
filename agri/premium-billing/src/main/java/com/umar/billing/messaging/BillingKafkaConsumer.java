package com.umar.billing.messaging;

import com.umar.billing.config.BillingProperties;
import com.umar.billing.service.BillingCoreService;
import com.umar.billing.service.BillingOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "billing.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BillingKafkaConsumer {

    private final BillingCoreService billingCoreService;
    private final BillingOutboxService outboxService;
    private final BillingProperties properties;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"platform.policy.events", "platform.identity.events"},
            groupId = "billing-cg"
    )
    public void onEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("eventType").asString("");
            switch (type) {
                case "policy.issued" -> {
                    String policyId = root.path("policyId").asString("");
                    if (!policyId.isBlank()) {
                        String payByStr = root.path("payBy").asString("");
                        billingCoreService.onPolicyIssued(
                                policyId,
                                root.path("farmerId").asString(""),
                                root.path("productId").asString(""),
                                root.path("premiumMinor").asLong(0),
                                root.path("subsidyMinor").asLong(0),
                                root.path("currency").asString("KES"),
                                root.path("payerType").asString("FARMER"),
                                payByStr.isBlank() ? Instant.now().plusSeconds(86400 * 7L) : Instant.parse(payByStr));
                    }
                }
                case "policy.cancelled", "policy.lapsed", "policy.expired" -> {
                    String policyId = root.path("policyId").asString("");
                    if (!policyId.isBlank()) {
                        billingCoreService.onPolicyCancelledOrEnded(policyId, type.replace("policy.", "").toUpperCase());
                    }
                }
                case "data.request.received" -> {
                    String reqId = root.path("requestId").asString("");
                    if (!reqId.isBlank()) {
                        outboxService.enqueue(properties.getKafka().getIdentityTopic(),
                                "data.request.part.completed", reqId, Map.of(
                                        "eventType", "data.request.part.completed",
                                        "requestId", reqId,
                                        "service", "billing-service",
                                        "outcome", "COMPLETED",
                                        "retainedCategories", List.of("PAYMENT_RECORD"),
                                        "timestamp", Instant.now().toString()));
                    }
                }
                default -> log.debug("Ignoring billing eventType {}", type);
            }
        } catch (Exception e) {
            log.warn("Failed to process event payload in BillingKafkaConsumer", e);
        }
    }
}
