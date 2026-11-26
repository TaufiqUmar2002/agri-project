package com.umar.riskpool.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "riskpool")
public class RiskPoolProperties {
    private Grpc grpc = new Grpc();
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Grpc {
        private int port = 9093;
        private String serviceToken = "dev-internal-service-token";
        private String allowedCallers = "policy-service";
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String ledgerTopic = "platform.ledger.events";
    }

    @Data
    public static class Rules {
        private long manualJournalSingleLimitMinor = 5_000_000L;
        private double maxRegionShareLimit = 0.60;
        private int minGroupSize = 5;
    }
}
