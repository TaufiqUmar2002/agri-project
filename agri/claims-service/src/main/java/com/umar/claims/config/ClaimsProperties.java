package com.umar.claims.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "claims")
public class ClaimsProperties {
    private Grpc grpc = new Grpc();
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Grpc {
        private int port = 9087;
        private String serviceToken = "dev-internal-service-token";
        private String allowedCallers = "payout-service";
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String claimTopic = "platform.claim.events";
    }

    @Data
    public static class Rules {
        private long autoApprovalLimitMinor = 100_000L;
        private long dualApprovalLimitMinor = 200_000L;
        private int maxPayoutAttempts = 5;
        private int retryCooldownMinutes = 10;
        private int disputeWindowDays = 30;
        private int maxDisputesPerClaim = 2;
    }
}
