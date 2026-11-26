package com.umar.billing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "billing")
public class BillingProperties {
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String billingTopic = "platform.billing.events";
        private String identityTopic = "platform.identity.events";
    }

    @Data
    public static class Rules {
        private double feeCapPercent = 2.0;
        private double discountCapPercent = 10.0;
        private int attemptExpiryMinutes = 5;
        private int callbackFreshnessMinutes = 5;
        private long refundSingleLimitMinor = 2_000_000L;
        private long bankTransferLimitMinor = 2_000_000L;
        private double minDepositPercent = 40.0;
        private int maxGraceDays = 14;
        private int maxReinstatementDays = 14;
        private int cashDisputeDays = 7;
    }
}
