package com.umar.auth.kafka;

import com.umar.auth.service.DataRightsService;
import com.umar.auth.service.KycService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.kafka", name = "enabled", havingValue = "true")
public class AuthKafkaConsumer {

    private final KycService kycService;
    private final DataRightsService dataRightsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "fraud.flagged", groupId = "${spring.kafka.consumer.group-id:auth-kyc-cg}")
    public void onFraudFlagged(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            UUID userId = UUID.fromString(node.get("userId").asString());
            String reason = node.path("reason").asString("FRAUD_SIGNAL");
            kycService.handleFraudFlag(userId, reason);
        } catch (Exception e) {
            log.warn("Failed handling fraud.flagged event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "data.request.part.completed", groupId = "${spring.kafka.consumer.group-id:auth-kyc-cg}")
    public void onDataRequestPartCompleted(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            UUID requestId = UUID.fromString(node.get("requestId").asString());
            String serviceName = node.path("serviceName").asString("unknown");
            List<String> retained = new ArrayList<>();
            if (node.has("retainedCategories") && node.get("retainedCategories").isArray()) {
                node.get("retainedCategories").forEach(n -> retained.add(n.asString()));
            }
            dataRightsService.handlePartCompleted(requestId, serviceName, retained);
        } catch (Exception e) {
            log.warn("Failed handling data.request.part.completed event: {}", e.getMessage());
        }
    }
}
