package com.umar.policy.messaging;

import com.umar.policy.config.PolicyProperties;
import com.umar.policy.service.PolicyLifecycleService;
import com.umar.policy.service.PolicyOutboxService;
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
@ConditionalOnProperty(prefix = "policy.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PolicyKafkaConsumer {

    private final PolicyLifecycleService lifecycleService;
    private final PolicyOutboxService outboxService;
    private final PolicyProperties properties;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"platform.billing.events", "platform.claims.events", "platform.payout.events", "platform.weather.events", "platform.identity.events"},
            groupId = "policy-service-cg"
    )
    public void onEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("eventType").asString("");
            switch (type) {
                case "premium.paid" -> {
                    String policyId = root.path("policyId").asString("");
                    boolean thresholdMet = root.path("activationThresholdMet").asBoolean(true);
                    String paidAtStr = root.path("paidAt").asString("");
                    Instant paidAt = paidAtStr.isBlank() ? Instant.now() : Instant.parse(paidAtStr);
                    if (!policyId.isBlank()) lifecycleService.onPremiumPaid(policyId, paidAt, thresholdMet);
                }
                case "claim.opened" -> {
                    String policyId = root.path("policyId").asString("");
                    if (!policyId.isBlank()) lifecycleService.onClaimOpened(policyId);
                }
                case "payout.completed" -> {
                    String policyId = root.path("policyId").asString("");
                    long amountMinor = root.path("amountMinor").asLong(0);
                    if (!policyId.isBlank() && amountMinor > 0) lifecycleService.onPayoutCompleted(policyId, amountMinor);
                }
                case "forecast.weather" -> {
                    String region = root.path("regionCode").asString("");
                    String status = root.path("enrollmentAction").asString("OPEN");
                    if (!region.isBlank()) {
                        lifecycleService.setRegionEnrollment(region, status, root.path("reason").asString("FORECAST"),
                                root.path("forecastId").asString(""));
                    }
                }
                case "data.request.received" -> {
                    String reqId = root.path("requestId").asString("");
                    if (!reqId.isBlank()) {
                        outboxService.enqueue(properties.getKafka().getIdentityTopic(),
                                "data.request.part.completed", reqId, Map.of(
                                        "eventType", "data.request.part.completed",
                                        "requestId", reqId,
                                        "service", "policy-service",
                                        "outcome", "COMPLETED",
                                        "retainedCategories", List.of("POLICY_RECORD"),
                                        "timestamp", Instant.now().toString()));
                    }
                }
                default -> log.debug("Ignoring eventType {}", type);
            }
        } catch (Exception e) {
            log.warn("Failed to process event payload in PolicyKafkaConsumer", e);
        }
    }
}
