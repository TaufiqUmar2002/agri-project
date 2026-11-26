package com.umar.plot.kafka;

import com.umar.plot.service.PlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plot.kafka", name = "enabled", havingValue = "true")
public class PlotKafkaConsumer {

    private final PlotService plotService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "platform.policy.events", groupId = "${spring.kafka.consumer.group-id:plot-registry-cg}")
    public void onPolicyEvent(String payload) {
        try {
            JsonNode n = objectMapper.readTree(payload);
            if ("policy.activated".equals(n.path("eventType").asString(""))) {
                plotService.onPolicyActivated(
                        n.get("plotId").asString(),
                        n.get("policyId").asString(),
                        Instant.parse(n.get("coverageStart").asString()),
                        Instant.parse(n.get("coverageEnd").asString()));
            }
        } catch (Exception e) {
            log.warn("Failed handling policy event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "platform.identity.events", groupId = "${spring.kafka.consumer.group-id:plot-registry-cg}")
    public void onIdentityEvent(String payload) {
        try {
            JsonNode n = objectMapper.readTree(payload);
            String type = n.path("eventType").asString("");
            if (("consent.withdrawn".equals(type) || "consent.recorded".equals(type))
                    && "SATELLITE_MONITORING".equals(n.path("purpose").asString(""))) {
                plotService.onConsentChanged(UUID.fromString(n.get("userId").asString()), "consent.recorded".equals(type));
            }
        } catch (Exception e) {
            log.warn("Failed handling identity event: {}", e.getMessage());
        }
    }
}
