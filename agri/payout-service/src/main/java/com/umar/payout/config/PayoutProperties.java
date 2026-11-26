package com.umar.payout.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "payout")
public class PayoutProperties {
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String payoutTopic = "platform.payout.events";
    }

    @Data
    public static class Rules {
        private int maxFallbackSwitches = 2;
        private int escalationHours = 24;
        private int voucherExpiryDays = 7;
        private int callbackSkewSeconds = 300;
    }
}
