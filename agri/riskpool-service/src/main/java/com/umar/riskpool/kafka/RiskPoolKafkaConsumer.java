package com.umar.riskpool.kafka;

import com.umar.riskpool.service.RiskPoolLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(prefix = "riskpool.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RiskPoolKafkaConsumer {

    private final RiskPoolLedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"platform.billing.events", "platform.payout.events", "platform.policy.events", "platform.claim.events"},
            groupId = "ledger-posting-cg")
    public void onEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("eventType").asString("");
            String eventId = node.path("eventId").asString(null);
            switch (type) {
                case "policy.issued" -> ledgerService.onPolicyIssued(eventId, node.path("policyId").asString(),
                        node.path("productId").asString("prd_1"), node.path("regionCode").asString("KE-NYERI"),
                        node.path("peril").asString("DROUGHT"), node.path("season").asString("2026-LR"),
                        node.path("sumInsuredMinor").asLong(100_000L), node.path("premiumMinor").asLong(10_000L),
                        node.path("subsidyMinor").asLong(0L), node.path("currency").asString("KES"));
                case "policy.activated" -> ledgerService.onPolicyActivated(eventId, node.path("policyId").asString(),
                        node.path("sumInsuredMinor").asLong(100_000L), LocalDate.now(), LocalDate.now().plusDays(90));
                case "premium.paid" -> ledgerService.onPremiumPaid(eventId, node.path("policyId").asString(),
                        node.path("amountMinor").asLong(0L), node.path("currency").asString("KES"),
                        node.path("method").asString("MOBILE_MONEY"));
                case "claim.opened" -> ledgerService.onClaimOpened(eventId, node.path("claimId").asString(),
                        node.path("policyId").asString(), node.path("amountMinor").asLong(0L),
                        node.path("currency").asString("KES"));
                case "payout.completed" -> ledgerService.onPayoutCompleted(eventId, node.path("claimId").asString(),
                        node.path("amountMinor").asLong(0L), node.path("currency").asString("KES"));
            }
        } catch (Exception ignored) {}
    }
}
