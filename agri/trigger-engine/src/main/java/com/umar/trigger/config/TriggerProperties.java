package com.umar.trigger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "trigger")
public class TriggerProperties {
    private Grpc grpc = new Grpc();
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Grpc {
        private int port = 9086;
        private String serviceToken = "dev-internal-service-token";
        private String statusAllowedCallers = "claims-service,payout-service,riskpool-service,audit-service";
        private String simulationAllowedCallers = "policy-service";
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String triggerTopic = "platform.trigger.events";
        private String auditTopic = "platform.audit.intake";
    }

    @Data
    public static class Rules {
        private int minSimulationSeasons = 10;
        private int simulationValidityDays = 90;
        private int largeEventThreshold = 100;
    }
}
