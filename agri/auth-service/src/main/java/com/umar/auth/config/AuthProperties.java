package com.umar.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private Security security = new Security();
    private Grpc grpc = new Grpc();
    private Kafka kafka = new Kafka();
    private Kyc kyc = new Kyc();
    private Limits limits = new Limits();

    @Data
    public static class Security {
        private boolean allowHeaderIdentity = true;
        private boolean devFallbackEnabled = true;
        private boolean requireMtls = false;
        private String hmacSecret = "AgriShieldAuthHmacSecretKey2026!";
        private String fieldEncryptionKey = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        private String issuer = "https://auth.agrishield.local";
        private String audience = "agrishield-platform";
        private int farmerAccessTtlSeconds = 900;
        private int staffAccessTtlSeconds = 600;
        private int farmerRefreshIdleDays = 14;
        private int farmerRefreshAbsoluteDays = 30;
        private int staffRefreshAbsoluteHours = 8;
        private int stepUpTtlSeconds = 600;
        private int otpTtlSeconds = 300;
        private int otpResendSeconds = 60;
        private int otpMaxAttempts = 5;
        private int loginMaxFailures = 5;
        private int lockoutSeconds = 900;
        private int escalatedLockoutSeconds = 3600;
        private List<String> allowedCountries = List.of("KE", "UG", "TZ", "RW", "IN", "GH", "NG", "ET");
        private List<String> supportedLanguages = List.of("en", "sw", "ki", "lg", "rw", "hi", "am");
        private List<String> allowedGrpcConsumers = List.of(
                "policy-service", "payout-service", "farmer-profile", "claims-service",
                "plot-registry", "integration-service", "auth-kyc-service");
    }

    @Data
    public static class Grpc {
        private boolean enabled = true;
        private int port = 9081;
        private Ssl ssl = new Ssl();

        @Data
        public static class Ssl {
            private boolean enabled = false;
            private String certChain = "certs/server/auth-server.crt";
            private String privateKey = "certs/server/auth-server.key";
            private String trustCert = "certs/ca/auth-ca.crt";
        }
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private boolean outboxEnabled = false;
        private long outboxDelayMs = 5000L;
        private String identityTopic = "platform.identity.events";
        private String securityTopic = "platform.identity.security.events";
    }

    @Data
    public static class Kyc {
        private BigDecimal autoVerifyThreshold = new BigDecimal("0.90");
        private BigDecimal manualReviewThreshold = new BigDecimal("0.70");
        private int caseExpiryDays = 14;
        private int providerFallbackHours = 24;
        private long maxFileBytes = 5_242_880L;
        private int reviewLockMinutes = 30;
    }

    @Data
    public static class Limits {
        private int agentDailyAttempts = 100;
        private int agentDailyConfirmations = 50;
        private int registerPerPhonePerHour = 3;
        private int registerPerIpPerHour = 20;
        private int loginPerAccountPer15min = 10;
        private int dataRightsPerUserPerDay = 3;
        private int kycSubmissionsPerUserPerDay = 5;
    }
}
