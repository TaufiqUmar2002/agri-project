package com.umar.notification.service.support;

import com.umar.enums.notification.NotificationCategory;
import com.umar.enums.notification.TemplateStatus;
import com.umar.notification.client.NotificationExternalClients;
import com.umar.notification.config.NotificationProperties;
import com.umar.notification.domain.MessageTemplateEntity;
import com.umar.notification.domain.NotificationPreferenceEntity;
import com.umar.notification.domain.OutboxEventEntity;
import com.umar.notification.exception.NotificationApiException;
import com.umar.notification.repository.MessageTemplateRepository;
import com.umar.notification.repository.NotificationPreferenceRepository;
import com.umar.notification.repository.OutboxEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationSupportService {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxRepository;
    private final MessageTemplateRepository templateRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationProperties properties;

    @PostConstruct
    public void seedDefaultTemplates() {
        createDefaultIfMissing("OTP_CODE", "en", NotificationCategory.SECURITY, true, true,
                "AgreeShield verification code: {{code}}. Do not share.");
        createDefaultIfMissing("OTP_CODE", "sw", NotificationCategory.SECURITY, true, true,
                "Nambari ya AgreeShield: {{code}}. Usishirikishe mtu.");
        createDefaultIfMissing("POLICY_ACTIVATED", "en", NotificationCategory.SERVICE, true, false,
                "AgreeShield policy {{policyId}} is now active for plot {{plotId}}.");
        createDefaultIfMissing("CLAIM_APPROVED", "en", NotificationCategory.SERVICE, true, false,
                "AgreeShield claim {{claimId}} approved. Payout will follow.");
        createDefaultIfMissing("CLAIM_UNDER_CHECK", "en", NotificationCategory.SERVICE, true, false,
                "AgreeShield claim {{claimId}} is undergoing verification.");
        createDefaultIfMissing("CONFIRM_PAYOUT_REQUIRED", "en", NotificationCategory.SERVICE, true, false,
                "Please confirm plot {{plotId}} via USSD *384*12# to release payout.");
        createDefaultIfMissing("PAYOUT_COMPLETED", "en", NotificationCategory.SERVICE, true, false,
                "Payout of {{amount}} completed. Ref: {{receiptRef}}.");
        createDefaultIfMissing("PAYOUT_FAILED", "en", NotificationCategory.SERVICE, true, false,
                "Payout attempt needs attention. Dial *384*12# for options.");
        createDefaultIfMissing("FROST_WARNING", "en", NotificationCategory.ADVISORY, false, false,
                "Weather alert for {{county}}: Frost expected tonight. Reply STOP to opt out.");
        createDefaultIfMissing("SEASON_RENEWAL", "en", NotificationCategory.PROMOTIONAL, false, false,
                "Renew your AgreeShield seasonal cover now. Reply STOP to opt out.");
    }

    private void createDefaultIfMissing(String code, String lang, NotificationCategory cat,
                                        boolean mandatory, boolean acceptsSecret, String smsText) {
        if (templateRepository.findFirstByCodeAndLanguageAndCurrentVersionTrue(code, lang).isEmpty()) {
            Instant now = Instant.now();
            templateRepository.save(MessageTemplateEntity.builder()
                    .id(UUID.randomUUID())
                    .code(code)
                    .language(lang)
                    .version(1)
                    .status(TemplateStatus.APPROVED)
                    .category(cat)
                    .mandatory(mandatory)
                    .acceptsSecret(acceptsSecret)
                    .variantsJson(toJson(Map.of("SMS", smsText, "WHATSAPP", smsText)))
                    .variablesJson(toJson(List.of()))
                    .checksJson(toJson(List.of(Map.of("check", "SEEDED", "passed", true))))
                    .approvalsJson(toJson(List.of(Map.of("approver", "system", "at", now.toString()))))
                    .providerStatus("APPROVED")
                    .currentVersion(true)
                    .lastEditedBy("system")
                    .approvedBy("compliance_sys")
                    .note("Default seeded template")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
    }

    public record ResolvedTemplate(MessageTemplateEntity template, boolean languageFallback) {}

    public ResolvedTemplate resolveApprovedTemplate(String code, String requestedLang) {
        String lang = requestedLang != null ? requestedLang : "en";
        Optional<MessageTemplateEntity> exact = templateRepository.findFirstByCodeAndLanguageAndCurrentVersionTrue(code, lang)
                .filter(t -> t.getStatus() == TemplateStatus.APPROVED);
        if (exact.isPresent()) {
            return new ResolvedTemplate(exact.get(), false);
        }
        Optional<MessageTemplateEntity> fallback = templateRepository.findFirstByCodeAndLanguageAndCurrentVersionTrue(code, "en")
                .filter(t -> t.getStatus() == TemplateStatus.APPROVED);
        if (fallback.isPresent()) {
            return new ResolvedTemplate(fallback.get(), !"en".equalsIgnoreCase(lang));
        }
        throw new NotificationApiException(HttpStatus.UNPROCESSABLE_CONTENT, "TEMPLATE_NOT_APPROVED",
                "No approved template found for " + code);
    }

    public String renderVariant(MessageTemplateEntity tpl, String channel, Map<String, Object> variables, String secretPlain) {
        Map<String, String> variants = fromJsonMapString(tpl.getVariantsJson());
        String raw = variants.getOrDefault(channel, variants.getOrDefault("SMS", tpl.getCode()));
        String rendered = raw;
        if (variables != null) {
            for (Map.Entry<String, Object> e : variables.entrySet()) {
                rendered = rendered.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
            }
        }
        if (secretPlain != null) {
            rendered = rendered.replace("{{code}}", secretPlain).replace("{{secret}}", secretPlain);
        }
        return rendered;
    }

    public NotificationPreferenceEntity getOrDefaultPreference(String farmerId, String defaultLang) {
        return preferenceRepository.findByFarmerId(farmerId).orElseGet(() -> NotificationPreferenceEntity.builder()
                .farmerId(farmerId)
                .language(defaultLang != null ? defaultLang : "sw")
                .channelsJson(toJson(List.of("SMS", "WHATSAPP")))
                .whatsappOptIn(true)
                .optOutJson(toJson(Map.of()))
                .quietHoursJson(toJson(Map.of("enabled", false, "start", "21:00", "end", "06:00")))
                .consentJson(toJson(List.of()))
                .historyJson(toJson(List.of()))
                .version(1)
                .updatedBy("default")
                .updatedAt(Instant.now())
                .build());
    }

    public boolean isQuietHoursActive(NotificationPreferenceEntity pref) {
        if (pref.getQuietHoursJson() == null) return false;
        Map<String, Object> qh = fromJsonMap(pref.getQuietHoursJson());
        return Boolean.TRUE.equals(qh.get("enabled")) && Boolean.TRUE.equals(qh.get("activeNow"));
    }

    public void emitOutbox(String eventType, String aggregateId, Object payloadObj) {
        outboxRepository.save(OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventType(eventType)
                .aggregateId(aggregateId)
                .topic(properties.getKafka().getNotificationTopic())
                .payload(toJson(payloadObj))
                .published(false)
                .createdAt(Instant.now())
                .build());
    }

    public void verifyHmacSignature(String secret, String signedData, String signature, Instant timestamp) {
        if (timestamp == null || Math.abs(Instant.now().getEpochSecond() - timestamp.getEpochSecond()) > properties.getRules().getCallbackSkewSeconds()) {
            throw new NotificationApiException(HttpStatus.UNAUTHORIZED, "CALLBACK_EXPIRED", "Timestamp outside allowed skew");
        }
        String expected = NotificationExternalClients.hmacSha256(secret, signedData);
        if (signature == null || !expected.equalsIgnoreCase(signature)) {
            throw new NotificationApiException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Invalid HMAC signature");
        }
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public Map<String, String> fromJsonMapString(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public List<String> fromJsonListString(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> fromJsonListMap(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public String idPrefix(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
