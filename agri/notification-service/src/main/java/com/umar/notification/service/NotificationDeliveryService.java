package com.umar.notification.service;

import com.umar.enums.notification.NotificationCategory;
import com.umar.enums.notification.NotificationStatus;
import com.umar.notification.client.NotificationExternalClients;
import com.umar.notification.config.NotificationProperties;
import com.umar.notification.domain.*;
import com.umar.notification.exception.NotificationApiException;
import com.umar.notification.repository.*;
import com.umar.notification.security.NotificationIdentityFilter;
import com.umar.notification.service.support.NotificationSupportService;
import com.umar.payload.request.notification.SendDirectMessageRequest;
import com.umar.payload.response.notification.NotificationListResponse;
import com.umar.payload.response.notification.NotificationStatusResponse;
import com.umar.payload.response.notification.SendDirectMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private static final Set<String> ALLOWED_SERVICES = Set.of(
            "auth-service", "payout-service", "claims-service", "policy-service", "finance-console");

    private static final Map<String, Set<String>> SERVICE_TEMPLATE_ALLOWLIST = Map.of(
            "auth-service", Set.of("OTP_CODE"),
            "payout-service", Set.of("PAYOUT_COMPLETED", "PAYOUT_FAILED", "CONFIRM_PAYOUT_REQUIRED"),
            "claims-service", Set.of("CLAIM_APPROVED", "CLAIM_UNDER_CHECK", "CONFIRM_PAYOUT_REQUIRED"),
            "policy-service", Set.of("POLICY_ACTIVATED"),
            "finance-console", Set.of("PAYOUT_COMPLETED", "PAYOUT_FAILED")
    );

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final SuppressionEntryRepository suppressionRepository;
    private final CallbackInboxRepository callbackInboxRepository;
    private final InboxEventRepository inboxRepository;
    private final NotificationExternalClients clients;
    private final NotificationSupportService support;
    private final NotificationProperties properties;

    @Transactional
    public NotificationEntity handleDomainEvent(String eventId, String topic, String eventType, Map<String, Object> payload) {
        if (eventId != null && inboxRepository.existsByEventId(eventId)) {
            return null;
        }
        if (eventId != null) {
            inboxRepository.save(InboxEventEntity.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .topic(topic)
                    .receivedAt(Instant.now())
                    .build());
        }

        String templateCode = mapEventToTemplate(eventType, payload);
        String farmerId = payload.get("farmerId") != null ? String.valueOf(payload.get("farmerId")) : "frm_001";
        String sourceRef = payload.get("claimId") != null ? String.valueOf(payload.get("claimId"))
                : (payload.get("policyId") != null ? String.valueOf(payload.get("policyId")) : String.valueOf(eventId));
        String dedupeKey = farmerId + ":" + templateCode + ":" + sourceRef;

        Instant cutoff = Instant.now().minusSeconds(properties.getRules().getDedupeWindowSeconds());
        Optional<NotificationEntity> existing = notificationRepository.findFirstByDedupeKeyAndCreatedAtAfter(dedupeKey, cutoff);
        if (existing.isPresent()) {
            return existing.get();
        }

        return createAndDispatch("FARMER", farmerId, templateCode, payload, null, null, null, sourceRef, eventId, dedupeKey, null);
    }

    private String mapEventToTemplate(String eventType, Map<String, Object> payload) {
        if ("policy.activated".equals(eventType)) return "POLICY_ACTIVATED";
        if ("claim.decision.ready".equals(eventType)) {
            String reason = payload.get("reason") != null ? String.valueOf(payload.get("reason")) : "";
            return (reason.contains("FRAUD") || reason.contains("REVIEW")) ? "CLAIM_UNDER_CHECK" : "CLAIM_APPROVED";
        }
        if ("claim.confirmation.required".equals(eventType)) return "CONFIRM_PAYOUT_REQUIRED";
        if ("payout.completed".equals(eventType)) return "PAYOUT_COMPLETED";
        if ("payout.failed".equals(eventType)) return "PAYOUT_FAILED";
        return "POLICY_ACTIVATED";
    }

    @Transactional
    public SendDirectMessageResponse sendDirectMessage(SendDirectMessageRequest req) {
        NotificationIdentityFilter.Identity id = NotificationIdentityFilter.current();
        String serviceId = id.serviceId();
        if (serviceId == null || !ALLOWED_SERVICES.contains(serviceId)) {
            throw new NotificationApiException(HttpStatus.FORBIDDEN, "SERVICE_NOT_ALLOWED", "Service token is not allow-listed");
        }
        Set<String> allowedTemplates = SERVICE_TEMPLATE_ALLOWLIST.getOrDefault(serviceId, Set.of());
        if (!allowedTemplates.contains(req.getTemplateCode())) {
            throw new NotificationApiException(HttpStatus.FORBIDDEN, "TEMPLATE_NOT_ALLOWED",
                    "Service " + serviceId + " cannot send template " + req.getTemplateCode());
        }

        String recipientType = req.getRecipient().getOrDefault("type", "FARMER");
        String recipientRef = req.getRecipient().get("ref");
        if (recipientRef == null || recipientRef.isBlank()) {
            throw new NotificationApiException(HttpStatus.UNPROCESSABLE_CONTENT, "RECIPIENT_INVALID", "Recipient ref is required");
        }
        if ("USER".equalsIgnoreCase(recipientType) || "STAFF".equalsIgnoreCase(recipientType)) {
            // Staff accounts route to email/console if no farmer contact
        } else if ("FARMER".equalsIgnoreCase(recipientType) && clients.findFarmerById(recipientRef).isEmpty()) {
            throw new NotificationApiException(HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND", "Farmer not found");
        }

        NotificationEntity entity = createAndDispatch(
                recipientType,
                recipientRef,
                req.getTemplateCode(),
                req.getVariables(),
                req.getSecret(),
                req.getExpiresInSeconds(),
                req.getChannelHint(),
                req.getSourceRef(),
                null,
                null,
                null
        );

        return SendDirectMessageResponse.builder()
                .notificationId(entity.getNotificationId())
                .status(entity.getStatus().name())
                .category(entity.getCategory().name())
                .channelPlan(support.fromJsonListString(entity.getChannelPlanJson()))
                .expiresAt(entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null)
                .version(entity.getVersion())
                .build();
    }

    @Transactional
    public NotificationEntity createAndDispatch(String recipientType, String recipientRef, String templateCode,
                                                Map<String, Object> variables, String secretPlain,
                                                Integer expiresInSeconds, String channelHint,
                                                String sourceRef, String sourceEventId,
                                                String dedupeKey, String broadcastId) {
        NotificationExternalClients.FarmerContact farmer = "FARMER".equalsIgnoreCase(recipientType)
                ? clients.findFarmerById(recipientRef).orElse(null)
                : null;
        NotificationPreferenceEntity pref = farmer != null
                ? support.getOrDefaultPreference(farmer.getFarmerId(), farmer.getLanguage())
                : NotificationPreferenceEntity.builder()
                        .farmerId(recipientRef).language("en")
                        .channelsJson(support.toJson(List.of("EMAIL", "CONSOLE")))
                        .optOutJson("{}").quietHoursJson("{}").version(1).build();

        NotificationSupportService.ResolvedTemplate resolved = support.resolveApprovedTemplate(templateCode, pref.getLanguage());
        MessageTemplateEntity tpl = resolved.template();
        if (secretPlain != null && !tpl.isAcceptsSecret()) {
            throw new NotificationApiException(HttpStatus.UNPROCESSABLE_CONTENT, "SECRET_NOT_ACCEPTED",
                    "Template " + templateCode + " does not accept secrets");
        }

        List<String> plan = buildChannelPlan(pref, farmer, tpl.getCategory(), channelHint);
        String msisdnHash = farmer != null ? farmer.getMsisdnHash() : NotificationExternalClients.sha256(recipientRef);

        if (plan.isEmpty()) {
            Instant now = Instant.now();
            NotificationEntity suppressed = notificationRepository.save(NotificationEntity.builder()
                    .id(UUID.randomUUID())
                    .notificationId(support.idPrefix("ntf_"))
                    .category(tpl.getCategory())
                    .templateCode(tpl.getCode())
                    .templateVersion(tpl.getVersion())
                    .farmerId(farmer != null ? farmer.getFarmerId() : null)
                    .recipientType(recipientType)
                    .recipientRef(recipientRef)
                    .msisdnHash(msisdnHash)
                    .language(tpl.getLanguage())
                    .languageFallback(resolved.languageFallback())
                    .channelPlanJson(support.toJson(List.of()))
                    .status(NotificationStatus.SUPPRESSED)
                    .suppressionReason("NO_ELIGIBLE_CHANNEL_OR_OPT_OUT")
                    .reasonCode("OPT_OUT")
                    .variablesJson(support.toJson(variables != null ? variables : Map.of()))
                    .sourceRef(sourceRef)
                    .sourceEventId(sourceEventId)
                    .dedupeKey(dedupeKey)
                    .broadcastId(broadcastId)
                    .failoverJson("[]")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            return suppressed;
        }

        Instant now = Instant.now();
        Instant expiresAt = expiresInSeconds != null ? now.plusSeconds(expiresInSeconds) : now.plus(7, ChronoUnit.DAYS);
        boolean ignoreQuietHours = tpl.getCategory() == NotificationCategory.SECURITY;
        if (!ignoreQuietHours && support.isQuietHoursActive(pref)) {
            return notificationRepository.save(NotificationEntity.builder()
                    .id(UUID.randomUUID())
                    .notificationId(support.idPrefix("ntf_"))
                    .category(tpl.getCategory())
                    .templateCode(tpl.getCode())
                    .templateVersion(tpl.getVersion())
                    .farmerId(farmer != null ? farmer.getFarmerId() : null)
                    .recipientType(recipientType)
                    .recipientRef(recipientRef)
                    .msisdnHash(msisdnHash)
                    .language(tpl.getLanguage())
                    .languageFallback(resolved.languageFallback())
                    .channelPlanJson(support.toJson(plan))
                    .currentChannel(plan.getFirst())
                    .status(NotificationStatus.DEFERRED)
                    .deferredUntil(now.plusSeconds(3600))
                    .variablesJson(support.toJson(variables != null ? variables : Map.of()))
                    .secretTokenRef(secretPlain != null ? clients.storeSecret(secretPlain) : null)
                    .sourceRef(sourceRef)
                    .sourceEventId(sourceEventId)
                    .dedupeKey(dedupeKey)
                    .broadcastId(broadcastId)
                    .failoverJson("[]")
                    .createdAt(now)
                    .expiresAt(expiresAt)
                    .updatedAt(now)
                    .build());
        }

        String secretRef = secretPlain != null ? clients.storeSecret(secretPlain) : null;
        String firstChannel = plan.getFirst();
        String rendered = support.renderVariant(tpl, firstChannel, variables, secretPlain);
        NotificationEntity entity = NotificationEntity.builder()
                .id(UUID.randomUUID())
                .notificationId(support.idPrefix("ntf_"))
                .category(tpl.getCategory())
                .templateCode(tpl.getCode())
                .templateVersion(tpl.getVersion())
                .farmerId(farmer != null ? farmer.getFarmerId() : null)
                .recipientType(recipientType)
                .recipientRef(recipientRef)
                .msisdnHash(msisdnHash)
                .language(tpl.getLanguage())
                .languageFallback(resolved.languageFallback())
                .channelPlanJson(support.toJson(plan))
                .currentChannel(firstChannel)
                .status(NotificationStatus.SENT)
                .variablesJson(support.toJson(variables != null ? variables : Map.of()))
                .renderedContent(secretPlain != null ? rendered.replace(secretPlain, "***REDACTED***") : rendered)
                .secretTokenRef(secretRef)
                .sourceRef(sourceRef)
                .sourceEventId(sourceEventId)
                .dedupeKey(dedupeKey)
                .broadcastId(broadcastId)
                .failoverJson("[]")
                .createdAt(now)
                .sentAt(now)
                .expiresAt(expiresAt)
                .updatedAt(now)
                .build();

        dispatchAttempt(entity, firstChannel, 1, rendered);
        if (secretRef != null) {
            clients.shredSecret(secretRef);
            entity.setSecretTokenRef(null);
        }
        entity = notificationRepository.save(entity);
        support.emitOutbox("notification.sent", entity.getNotificationId(),
                Map.of("notificationId", entity.getNotificationId(), "channel", firstChannel, "templateCode", tpl.getCode()));
        return entity;
    }

    private void dispatchAttempt(NotificationEntity entity, String channel, int attemptNumber, String rendered) {
        String providerId = clients.dispatchToAggregator("africastalking", channel, entity.getNotificationId(), rendered);
        Instant now = Instant.now();
        attemptRepository.save(NotificationAttemptEntity.builder()
                .id(UUID.randomUUID())
                .attemptId(support.idPrefix("att_"))
                .notificationId(entity.getNotificationId())
                .attemptNumber(attemptNumber)
                .channel(channel)
                .aggregator("africastalking")
                .providerMessageId(providerId)
                .status("SENT")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private List<String> buildChannelPlan(NotificationPreferenceEntity pref,
                                          NotificationExternalClients.FarmerContact farmer,
                                          NotificationCategory category,
                                          String channelHint) {
        List<String> base = new ArrayList<>(support.fromJsonListString(pref.getChannelsJson()));
        if (base.isEmpty()) base.add("SMS");
        if (channelHint != null && base.remove(channelHint)) {
            base.addFirst(channelHint);
        }
        if (farmer != null && !farmer.isWhatsappCapable()) {
            base.remove("WHATSAPP");
        }
        if (category == NotificationCategory.ADVISORY || category == NotificationCategory.PROMOTIONAL) {
            Map<String, Object> optOut = support.fromJsonMap(pref.getOptOutJson());
            Object optedOutObj = optOut.get(category.name());
            if (optedOutObj instanceof List<?> list) {
                for (Object ch : list) {
                    base.remove(String.valueOf(ch));
                }
            }
            if (farmer != null) {
                List<SuppressionEntryEntity> sup = suppressionRepository.findByMsisdnHashAndActiveTrue(farmer.getMsisdnHash());
                for (SuppressionEntryEntity s : sup) {
                    if ("ALL".equals(s.getCategory()) || category.name().equals(s.getCategory())) {
                        if ("ALL".equals(s.getChannel())) {
                            base.clear();
                        } else {
                            base.remove(s.getChannel());
                        }
                    }
                }
            }
        }
        if (category == NotificationCategory.SECURITY && base.isEmpty()) {
            base.add("SMS");
        }
        return base;
    }

    @Transactional(readOnly = true)
    public NotificationStatusResponse getStatus(String notificationId, String ifNoneMatch) {
        NotificationEntity entity = notificationRepository.findByNotificationId(notificationId)
                .orElseThrow(() -> new NotificationApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notification not found"));
        NotificationIdentityFilter.Identity id = NotificationIdentityFilter.current();
        if (id.hasRole("FARMER") && !id.userId().equals(entity.getFarmerId()) && !id.userId().equals(entity.getRecipientRef())) {
            throw new NotificationApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot view another farmer notification");
        }
        String etag = "\"v" + entity.getVersion() + "\"";
        if (etag.equals(ifNoneMatch)) {
            throw new NotificationApiException(HttpStatus.NOT_MODIFIED, "NOT_MODIFIED", "Not modified");
        }

        List<NotificationAttemptEntity> attempts = attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notificationId);
        List<Map<String, Object>> attemptViews = attempts.stream().map(a -> Map.<String, Object>of(
                "attemptNumber", a.getAttemptNumber(),
                "channel", a.getChannel(),
                "status", a.getStatus(),
                "reasonCode", a.getReasonCode() != null ? a.getReasonCode() : ""
        )).toList();

        boolean isFarmer = id.hasRole("FARMER") && !id.hasAnyRole("SUPPORT", "ADMIN", "COMPLIANCE_OFFICER");
        boolean showContent = id.hasAnyRole("ADMIN", "COMPLIANCE_OFFICER");

        return NotificationStatusResponse.builder()
                .notificationId(entity.getNotificationId())
                .category(entity.getCategory().name())
                .templateCode(entity.getTemplateCode())
                .templateVersion(entity.getTemplateVersion())
                .status(entity.getStatus().name())
                .language(entity.getLanguage())
                .languageFallback(entity.isLanguageFallback())
                .channelPlan(support.fromJsonListString(entity.getChannelPlanJson()))
                .currentChannel(entity.getCurrentChannel())
                .deferredUntil(entity.getDeferredUntil() != null ? entity.getDeferredUntil().toString() : null)
                .suppressionReason(entity.getSuppressionReason())
                .reasonCode(isFarmer ? null : entity.getReasonCode())
                .attempts(isFarmer ? null : attemptViews)
                .failover(support.fromJsonListString(entity.getFailoverJson()))
                .content(showContent && entity.getRenderedContent() != null ? Map.of("body", entity.getRenderedContent()) : null)
                .createdAt(entity.getCreatedAt().toString())
                .sentAt(entity.getSentAt() != null ? entity.getSentAt().toString() : null)
                .deliveredAt(entity.getDeliveredAt() != null ? entity.getDeliveredAt().toString() : null)
                .expiresAt(entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null)
                .version(entity.getVersion())
                .build();
    }

    @Transactional
    public Map<String, Object> processDeliveryCallback(String aggregator, String signature, String timestampHeader,
                                                       String nonce, Map<String, Object> body) {
        Instant ts = timestampHeader != null ? Instant.parse(timestampHeader) : null;
        String msgId = String.valueOf(body.get("providerMessageId"));
        support.verifyHmacSignature(properties.getRules().getAggregatorHmacSecret(),
                (timestampHeader != null ? timestampHeader : "") + ":" + aggregator + ":" + msgId, signature, ts);
        if (nonce == null || callbackInboxRepository.existsByNonce(nonce)) {
            throw new NotificationApiException(HttpStatus.CONFLICT, "CALLBACK_REPLAY", "Duplicate callback nonce");
        }
        callbackInboxRepository.save(CallbackInboxEntity.builder()
                .id(UUID.randomUUID()).nonce(nonce).aggregator(aggregator).eventType("DELIVERY").receivedAt(Instant.now()).build());

        NotificationAttemptEntity attempt = attemptRepository.findByProviderMessageId(msgId)
                .orElseThrow(() -> new NotificationApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Provider message not found"));
        NotificationEntity notif = notificationRepository.findByNotificationId(attempt.getNotificationId())
                .orElseThrow(() -> new NotificationApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notification not found"));

        String cbStatus = String.valueOf(body.getOrDefault("status", "DELIVERED"));
        String reason = body.get("reasonCode") != null ? String.valueOf(body.get("reasonCode")) : null;
        attempt.setStatus(cbStatus);
        attempt.setReasonCode(reason);
        attempt.setUpdatedAt(Instant.now());
        attemptRepository.save(attempt);

        if ("DELIVERED".equalsIgnoreCase(cbStatus)) {
            notif.setStatus(NotificationStatus.DELIVERED);
            notif.setDeliveredAt(Instant.now());
            notif.setUpdatedAt(Instant.now());
            notificationRepository.save(notif);
        } else if ("FAILED".equalsIgnoreCase(cbStatus)) {
            boolean switched = triggerFailover(notif, reason);
            if (!switched) {
                notif.setStatus(NotificationStatus.FAILED);
                notif.setReasonCode(reason);
                notif.setUpdatedAt(Instant.now());
                notificationRepository.save(notif);
                support.emitOutbox("notification.failed", notif.getNotificationId(),
                        Map.of("notificationId", notif.getNotificationId(), "reasonCode", reason != null ? reason : "DELIVERY_FAILED"));
            }
        }
        return Map.of("accepted", true, "notificationId", notif.getNotificationId(), "status", notif.getStatus().name());
    }

    public boolean triggerFailover(NotificationEntity notif, String reason) {
        List<String> plan = support.fromJsonListString(notif.getChannelPlanJson());
        int idx = notif.getCurrentChannel() != null ? plan.indexOf(notif.getCurrentChannel()) : -1;
        if (idx >= 0 && idx + 1 < plan.size()) {
            String nextChannel = plan.get(idx + 1);
            List<String> failovers = support.fromJsonListString(notif.getFailoverJson());
            failovers.add(notif.getCurrentChannel() + "->" + nextChannel + ":" + (reason != null ? reason : "TIMEOUT"));
            notif.setFailoverJson(support.toJson(failovers));
            notif.setCurrentChannel(nextChannel);
            notif.setStatus(NotificationStatus.SENT);
            notif.setSentAt(Instant.now());
            notif.setUpdatedAt(Instant.now());
            int attemptCount = attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notif.getNotificationId()).size();
            dispatchAttempt(notif, nextChannel, attemptCount + 1, notif.getRenderedContent() != null ? notif.getRenderedContent() : "");
            notificationRepository.save(notif);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse listNotifications(String farmerId, String statusFilter) {
        NotificationIdentityFilter.Identity id = NotificationIdentityFilter.current();
        if (id.hasRole("FARMER") && !id.hasAnyRole("SUPPORT", "ADMIN", "COMPLIANCE_OFFICER") && farmerId != null && !farmerId.equals(id.userId())) {
            throw new NotificationApiException(HttpStatus.FORBIDDEN, "FILTER_NOT_ALLOWED", "Farmer cannot filter another farmerId");
        }
        String effectiveFarmerId = (id.hasRole("FARMER") && !id.hasAnyRole("SUPPORT", "ADMIN", "COMPLIANCE_OFFICER"))
                ? id.userId()
                : farmerId;

        List<NotificationEntity> all = effectiveFarmerId != null
                ? notificationRepository.findByFarmerIdOrderByCreatedAtDesc(effectiveFarmerId)
                : notificationRepository.findAll();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (NotificationEntity n : all) {
            counts.merge(n.getStatus().name(), 1L, Long::sum);
            if (statusFilter != null && !statusFilter.equalsIgnoreCase(n.getStatus().name())) {
                continue;
            }
            items.add(Map.of(
                    "notificationId", n.getNotificationId(),
                    "templateCode", n.getTemplateCode(),
                    "category", n.getCategory().name(),
                    "status", n.getStatus().name(),
                    "channel", n.getCurrentChannel() != null ? n.getCurrentChannel() : "",
                    "createdAt", n.getCreatedAt().toString()
            ));
        }

        List<Map<String, Object>> aggregators = id.hasAnyRole("ADMIN", "SUPPORT")
                ? List.of(Map.of("name", "africastalking", "status", "UP", "circuit", "CLOSED"))
                : null;

        return NotificationListResponse.builder()
                .items(items)
                .counts(counts)
                .aggregators(aggregators)
                .build();
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public int runSweeper() {
        int processed = 0;
        Instant now = Instant.now();
        for (NotificationEntity n : notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.DEFERRED)) {
            if (n.getDeferredUntil() != null && !now.isBefore(n.getDeferredUntil())) {
                List<String> plan = support.fromJsonListString(n.getChannelPlanJson());
                String channel = plan.isEmpty() ? "SMS" : plan.getFirst();
                String secretPlain = n.getSecretTokenRef() != null ? clients.resolveSecret(n.getSecretTokenRef()).orElse(null) : null;
                NotificationSupportService.ResolvedTemplate tpl = support.resolveApprovedTemplate(n.getTemplateCode(), n.getLanguage());
                String rendered = support.renderVariant(tpl.template(), channel, support.fromJsonMap(n.getVariablesJson()), secretPlain);
                n.setCurrentChannel(channel);
                n.setStatus(NotificationStatus.SENT);
                n.setSentAt(now);
                n.setRenderedContent(secretPlain != null ? rendered.replace(secretPlain, "***REDACTED***") : rendered);
                dispatchAttempt(n, channel, 1, rendered);
                if (n.getSecretTokenRef() != null) {
                    clients.shredSecret(n.getSecretTokenRef());
                    n.setSecretTokenRef(null);
                }
                n.setUpdatedAt(now);
                notificationRepository.save(n);
                processed++;
            }
        }
        for (NotificationEntity n : notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.SENT)) {
            if (n.getExpiresAt() != null && now.isAfter(n.getExpiresAt())) {
                n.setStatus(NotificationStatus.EXPIRED);
                if (n.getSecretTokenRef() != null) {
                    clients.shredSecret(n.getSecretTokenRef());
                    n.setSecretTokenRef(null);
                }
                n.setUpdatedAt(now);
                notificationRepository.save(n);
                processed++;
            }
        }
        return processed;
    }
}
