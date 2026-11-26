package com.umar.policy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "policy")
public class PolicyProperties {
    private Grpc grpc = new Grpc();
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Grpc {
        private int port = 9084;
        private List<String> allowedCallers = List.of(
                "trigger-engine", "claims-service", "payout-service",
                "riskpool-service", "farmer-profile", "notification-service");
        private String internalToken = "local-internal-service-token";
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String policyTopic = "platform.policy.events";
        private String identityTopic = "platform.identity.events";
    }

    @Data
    public static class Rules {
        private int quoteValidityHours = 72;
        private int backtestMinSeasons = 10;
        private double lossRatioMin = 0.55;
        private double lossRatioMax = 0.75;
        private double fallbackTariffLoadingPercent = 5.0;
        private int coolingOffDays = 14;
        private int holdExpiryDays = 14;
        private int activationRetryHours = 24;
    }
}
