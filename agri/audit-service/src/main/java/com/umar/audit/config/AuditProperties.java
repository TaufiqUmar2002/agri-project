package com.umar.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {
    private Kafka kafka = new Kafka();
    private Rules rules = new Rules();

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String auditTopic = "platform.audit.events";
    }

    @Data
    public static class Rules {
        private int maxUnscopedSearchDays = 31;
        private int maxAcceptRiskMonths = 12;
        private long clockSkewSeconds = 300;
        private String signingKeyId = "kms_audit_key_v1";
        private String signingSecret = "agreeshield-audit-kms-secret";
    }
}
