package com.umar.claims.kafka;

import com.umar.claims.service.ClaimsSagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "claims.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ClaimsKafkaConsumer {

    private final ClaimsSagaService sagaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"platform.trigger.events", "platform.payout.events", "platform.policy.events"},
            groupId = "claims-service-cg")
    public void onEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("eventType").asString("");
            String eventId = node.path("eventId").asString(null);
            switch (type) {
                case "threshold.breached" -> sagaService.onThresholdBreached(
                        eventId, node.path("decisionId").asString(), node.path("policyId").asString(),
                        node.path("ruleId").asString("rul_1"), node.path("ruleVersion").asInt(1),
                        node.path("windowId").asString("W1"), node.path("tierId").asString("T1"),
                        node.path("payoutPercent").asDouble(50.0), Instant.now());
                case "payout.initiated", "payout.completed", "payout.failed", "payout.escalated" ->
                        sagaService.onPayoutEvent(eventId, type, node.path("claimId").asString(),
                                node.path("payoutId").asString(null), node.path("amountMinor").asLong(0),
                                node.path("reasonCode").asString(null), node.path("nextStep").asString(null));
                case "trigger.decision.revised" -> sagaService.onTriggerDecisionRevised(
                        eventId, node.path("decisionId").asString(), node.path("revisedPayoutPercent").asDouble(0.0));
                case "policy.expired" -> sagaService.onPolicyExpired(eventId, node.path("policyId").asString());
            }
        } catch (Exception ignored) {}
    }
}
