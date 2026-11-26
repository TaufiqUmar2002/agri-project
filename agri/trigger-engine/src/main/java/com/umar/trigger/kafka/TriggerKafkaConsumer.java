package com.umar.trigger.kafka;

import com.umar.trigger.service.TriggerEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(prefix = "trigger.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TriggerKafkaConsumer {

    private final TriggerEvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"platform.policy.events", "observation.sensor", "observation.weather"}, groupId = "trigger-engine-cg")
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("eventType").asString("");
            if ("policy.activated".equals(type)) {
                evaluationService.onPolicyActivated(
                        node.path("eventId").asString(null),
                        node.path("policyId").asString(),
                        node.path("farmerId").asString(),
                        node.path("plotId").asString("plt_1"),
                        node.path("regionCode").asString("KE-NYERI"),
                        node.path("productId").asString("prd_1"),
                        node.path("ruleId").asString("rul_1"),
                        node.path("ruleVersion").asInt(1),
                        node.path("sumInsuredMinor").asLong(100_000L),
                        node.path("currency").asString("KES"));
            } else if (node.has("ingestId")) {
                evaluationService.onObservationIngested(
                        node.path("ingestId").asString(),
                        node.path("sourceId").asString("src_iot"),
                        node.path("districtCode").asString("KE-NYERI"),
                        node.path("unitId").asString(null),
                        node.path("channel").asString("rain.mm"),
                        node.path("value").asDouble(0.0),
                        LocalDate.now(),
                        node.path("qualityFlags").toString(),
                        node.path("revision").asInt(0));
            }
        } catch (Exception ignored) {}
    }
}
