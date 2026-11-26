package com.umar.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "hub")
public class HubProperties {

    private Auth auth = new Auth();
    private Grpc grpc = new Grpc();
    private Kafka kafka = new Kafka();
    private Vault vault = new Vault();
    private Webhook webhook = new Webhook();
    private Extension extension = new Extension();
    private Quotas quotas = new Quotas();

    @Data
    public static class Auth {
        private boolean allowHeaderIdentity = true;
        private boolean devFallbackEnabled = true;
        private boolean requireMtls = false;
    }

    @Data
    public static class Grpc {
        private boolean enabled = true;
        private int port = 9089;
        private Ssl ssl = new Ssl();

        @Data
        public static class Ssl {
            private boolean enabled = false;
            private String certChain = "certs/server/hub-server.crt";
            private String privateKey = "certs/server/hub-server.key";
            private String trustCert = "certs/ca/hub-ca.crt";
        }
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private boolean outboxEnabled = false;
        private long outboxDelayMs = 5000;
        private String integrationTopic = "platform.integration.events";
        private String identityTopic = "identity.data-rights.events";
    }

    @Data
    public static class Vault {
        private String mode = "local";
        private String baseUrl = "https://localhost:8443";
        private String grpcHost = "localhost";
        private int grpcPort = 9443;
    }

    @Data
    public static class Webhook {
        private int deliveryTimeoutMs = 10000;
        private int challengeTimeoutMs = 5000;
        private int maxPayloadBytes = 262144;
        private int retryWindowHours = 72;
        private int keyOverlapHours = 72;
        private int defaultRateLimitPerSecond = 20;
        private double autoPauseSuccessThreshold = 0.50;
        private int autoPauseWindowMinutes = 60;
        private int replayMaxHours = 24;
        private int replayMaxEvents = 100000;
        private int replayRetentionDays = 90;
        private int replayJobsPerDay = 5;
        private int testRateLimitPerHour = 10;
        private List<Integer> allowedPorts = List.of(443, 8443);
        private boolean allowLoopbackInTest = false;
    }

    @Data
    public static class Extension {
        private int defaultTimeoutMs = 2000;
        private int maxSyncTimeoutMs = 5000;
        private int maxMemoryMb = 128;
        private int autoDisableErrorRatePercent = 20;
        private int autoDisableWindowMinutes = 5;
        private int autoDisableConsecutiveFailures = 20;
        private int killPropagationSeconds = 10;
        private int normalPropagationSeconds = 30;
    }

    @Data
    public static class Quotas {
        private long maxWebhookDeliveriesPerMonth = 50_000_000L;
        private int maxWebhookRatePerSecond = 500;
        private long maxInboundRecordsPerMonth = 50_000_000L;
        private long maxExtensionInvocationsPerMonth = 50_000_000L;
        private int maxSubscriptionsPerPartner = 50;
        private int maxConnectorsPerPartner = 25;
    }
}
