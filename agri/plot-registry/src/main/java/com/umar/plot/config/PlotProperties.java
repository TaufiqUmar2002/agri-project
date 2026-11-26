package com.umar.plot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "plot")
public class PlotProperties {

    private Security security = new Security();
    private Grpc grpc = new Grpc();
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Security {
        private boolean allowHeaderIdentity = true;
        private boolean devFallbackEnabled = true;
        private List<String> allowedGrpcConsumers = List.of(
                "trigger-engine", "riskpool-service", "policy-service", "farmer-profile-service",
                "claims-service", "crop-health-ai", "yield-ai", "fraud-ai", "plot-registry-service");
        private List<String> streamConsumers = List.of("trigger-engine", "riskpool-service");
    }

    @Data
    public static class Grpc {
        private boolean enabled = true;
        private int port = 9083;
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private boolean outboxEnabled = false;
        private long outboxDelayMs = 5000L;
        private String plotTopic = "platform.plot.events";
        private String identityTopic = "platform.identity.events";
    }

    @Data
    public static class Rules {
        private String currentVersion = "2026.10.1";
        private double minAreaHa = 0.25;
        private double maxAreaHa = 50.0;
        private double minWidthM = 20.0;
        private double maxExclusionPercent = 5.0;
        private double croplandValidatePercent = 70.0;
        private double croplandHoldPercent = 50.0;
        private double overlapIgnorePercent = 5.0;
        private double overlapReviewPercent = 10.0;
        private double overlapDuplicatePercent = 90.0;
        private int maxPlotsPerFarmer = 10;
        private List<String> supportedCountries = List.of("KE", "UG", "TZ", "RW", "IN", "GH", "NG", "ET");
    }
}
