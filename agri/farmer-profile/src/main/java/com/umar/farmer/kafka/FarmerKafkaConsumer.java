package com.umar.farmer.kafka;

import com.umar.enums.auth.KycTier;
import com.umar.farmer.service.FarmerProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "farmer.kafka", name = "enabled", havingValue = "true")
public class FarmerKafkaConsumer {

    private final FarmerProfileService profileService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "platform.identity.events", groupId = "${spring.kafka.consumer.group-id:farmer-profile-cg}")
    public void onIdentityEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("eventType").asString("");
            if ("kyc.verified".equals(type)) {
                UUID userId = UUID.fromString(node.get("userId").asString());
                KycTier tier = KycTier.valueOf(node.path("tier").asString("TIER_1"));
                String legalNameToken = node.path("legalNameToken").asString("vt_name_" + userId);
                profileService.onKycVerified(userId, tier, legalNameToken);
            } else if ("data.request.received".equals(type)) {
                UUID reqId = UUID.fromString(node.get("requestId").asString());
                UUID userId = UUID.fromString(node.get("userId").asString());
                profileService.onDataRightsReceived(reqId, userId, node.path("type").asString("EXPORT"));
            }
        } catch (Exception e) {
            log.warn("Failed handling identity event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.flagged", groupId = "${spring.kafka.consumer.group-id:farmer-profile-cg}")
    public void onFraudFlagged(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("userId")) {
                profileService.onFraudFlagged(UUID.fromString(node.get("userId").asString()));
            }
        } catch (Exception e) {
            log.warn("Failed handling fraud.flagged: {}", e.getMessage());
        }
    }
}
