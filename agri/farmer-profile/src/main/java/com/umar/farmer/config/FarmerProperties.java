package com.umar.farmer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "farmer")
public class FarmerProperties {

    private Security security = new Security();
    private Grpc grpc = new Grpc();
    private Kafka kafka = new Kafka();
    private Wallet wallet = new Wallet();
    private Catalogue catalogue = new Catalogue();

    @Data
    public static class Security {
        private boolean allowHeaderIdentity = true;
        private boolean devFallbackEnabled = true;
        private String hmacSecret = "AgriShieldFarmerHmacSecret2026!";
        private List<String> allowedGrpcConsumers = List.of(
                "payout-service", "policy-service", "notification-service",
                "claims-service", "plot-registry", "advisory-bot", "farmer-profile-service");
        private List<String> payoutDestinationConsumers = List.of("payout-service", "policy-service");
    }

    @Data
    public static class Grpc {
        private boolean enabled = true;
        private int port = 9082;
        private Ssl ssl = new Ssl();

        @Data
        public static class Ssl {
            private boolean enabled = false;
        }
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private boolean outboxEnabled = false;
        private long outboxDelayMs = 5000L;
        private String farmerTopic = "platform.farmer.events";
        private String identityTopic = "platform.identity.events";
    }

    @Data
    public static class Wallet {
        private int maxPerFarmer = 3;
        private int maxAttemptsPerDay = 3;
        private BigDecimal nameMatchVerifiedThreshold = new BigDecimal("0.90");
        private BigDecimal nameMatchReviewThreshold = new BigDecimal("0.70");
        private int primaryCoolingOffHours = 24;
        private int newDeviceCoolingOffHours = 48;
        private List<String> supportedProviders = List.of("MPESA", "MTN_MOMO", "AIRTEL_MONEY", "TIGO_PESA");
    }

    @Data
    public static class Catalogue {
        private List<String> supportedLanguages = List.of("en", "sw", "ki", "lg", "rw", "hi", "am");
        private List<String> supportedCountries = List.of("KE", "UG", "TZ", "RW", "IN", "GH", "NG", "ET");
        private List<String> supportedCrops = List.of(
                "MAIZE", "BEANS", "WHEAT", "RICE", "SORGHUM", "MILLET", "COFFEE", "TEA", "CASSAVA", "COTTON");
    }
}
