package com.umar.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String notificationTopic = "platform.notification.events";
    }

    @Data
    public static class Rules {
        private long dedupeWindowSeconds = 600;
        private long ussdSessionTtlSeconds = 90;
        private long callbackSkewSeconds = 300;
        private long broadcastDualControlThreshold = 5000;
        private int maxSmsSegments = 3;
        private String ussdHmacSecret = "agreeshield-ussd-secret";
        private String aggregatorHmacSecret = "agreeshield-agg-secret";
        private long sweeperAckTimeoutSeconds = 120;
    }
}
