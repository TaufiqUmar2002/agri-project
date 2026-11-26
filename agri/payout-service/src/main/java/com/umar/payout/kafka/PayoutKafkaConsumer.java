package com.umar.payout.kafka;

import com.umar.payout.service.PayoutExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "payout.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PayoutKafkaConsumer {

    private final PayoutExecutionService executionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "platform.claim.events", groupId = "payout-service-cg")
    public void onClaimEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("eventType").asString("");
            String eventId = node.path("eventId").asString(null);
            switch (type) {
                case "claim.approved", "claim.retry.requested" -> executionService.onClaimReadyForPayout(
                        eventId, type, node.path("claimId").asString(),
                        node.path("attempt").asInt(1), node.path("farmerId").asString("frm_default"),
                        node.path("route").asString("SAME_WALLET"), node.path("destinationId").asString(null));
                case "claim.payout.cancel.requested" -> executionService.onCancelRequested(
                        eventId, node.path("claimId").asString());
            }
        } catch (Exception ignored) {}
    }
}
