package com.umar.notification.service;

import com.umar.enums.notification.NotificationCategory;
import com.umar.enums.notification.TemplateStatus;
import com.umar.notification.client.NotificationExternalClients;
import com.umar.notification.config.NotificationProperties;
import com.umar.notification.domain.*;
import com.umar.notification.exception.NotificationApiException;
import com.umar.notification.repository.*;
import com.umar.notification.security.NotificationIdentityFilter;
import com.umar.notification.service.support.NotificationSupportService;
import com.umar.payload.request.notification.*;
import com.umar.payload.response.notification.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationChannelsAndTemplatesService {

    private static final Set<String> OPTIONAL_CATEGORIES = Set.of("ADVISORY", "PROMOTIONAL");
    private static final Set<String> MANDATORY_CATEGORIES = Set.of("SERVICE", "SECURITY");

    private final NotificationPreferenceRepository preferenceRepository;
    private final MessageTemplateRepository templateRepository;
    private final BroadcastAlertRepository broadcastRepository;
    private final UssdSessionRepository ussdRepository;
    private final SuppressionEntryRepository suppressionRepository;
    private final CallbackInboxRepository callbackInboxRepository;
    private final NotificationExternalClients clients;
    private final NotificationSupportService support;
    private final NotificationDeliveryService deliveryService;
    private final NotificationProperties properties;

    @Transactional
    public Map<String, Object> handleUssdSession(String signature, String timestampHeader, UssdSessionRequest req) {
        Instant ts = timestampHeader != null ? Instant.parse(timestampHeader) : null;
        String signedData = (timestampHeader != null ? timestampHeader : "") + ":" + req.getServiceCode() + ":" + req.getSessionId();
        support.verifyHmacSignature(properties.getRules().getUssdHmacSecret(), signedData, signature, ts);

        String msisdnHash = NotificationExternalClients.sha256(req.getMsisdn());
        Optional<NotificationExternalClients.FarmerContact> farmerOpt = clients.findFarmerByMsisdnHash(msisdnHash);
        if (farmerOpt.isEmpty()) {
            return Map.of("action", "END", "message", cap182("NUMBER_NOT_REGISTERED: Visit an AgreeShield agent."));
        }
        NotificationExternalClients.FarmerContact farmer = farmerOpt.get();
        Instant now = Instant.now();

        UssdSessionEntity session = ussdRepository.findBySessionId(req.getSessionId()).orElseGet(() -> UssdSessionEntity.builder()
                .id(UUID.randomUUID())
                .sessionId(req.getSessionId())
                .serviceCode(req.getServiceCode())
                .msisdnHash(msisdnHash)
                .farmerId(farmer.getFarmerId())
                .networkCode(req.getNetworkCode())
                .state("ROOT")
                .expiresAt(now.plusSeconds(properties.getRules().getUssdSessionTtlSeconds()))
                .updatedAt(now)
                .build());

        if (session.getExpiresAt().isBefore(now)) {
            session.setState("ROOT");
        }
        session.setExpiresAt(now.plusSeconds(properties.getRules().getUssdSessionTtlSeconds()));
        session.setUpdatedAt(now);

        String input = req.getInput() == null ? "" : req.getInput().trim();
        String action = "CON";
        String msg;
        if (input.isEmpty() && "ROOT".equals(session.getState())) {
            session.setState("MENU");
            msg = "AgreeShield\n1. Policy Status\n2. Payouts\n3. Confirm Plot\n4. Settings\n0. Help";
        } else if ("1".equals(input)) {
            action = "END";
            msg = "Policy " + farmer.getActivePolicyId() + " is ACTIVE for plot " + farmer.getPlotId() + ".";
        } else if ("2".equals(input)) {
            action = "END";
            msg = "No failed payouts pending for " + farmer.getFarmerId() + ".";
        } else if ("3".equals(input)) {
            session.setState("CONFIRM_PLOT");
            msg = "Confirm plot " + farmer.getPlotId() + "?\n1. Yes confirm\n2. Cancel";
        } else if ("CONFIRM_PLOT".equals(session.getState()) && "1".equals(input)) {
            clients.confirmPlotViaUssd(farmer.getPlotId(), farmer.getFarmerId());
            action = "END";
            msg = "Plot " + farmer.getPlotId() + " confirmed. Thank you.";
        } else if ("4".equals(input)) {
            action = "END";
            NotificationPreferenceEntity pref = support.getOrDefaultPreference(farmer.getFarmerId(), farmer.getLanguage());
            msg = "Language: " + pref.getLanguage() + ", Channels: " + pref.getChannelsJson();
        } else if ("0".equals(input)) {
            action = "END";
            msg = "Call 0800-AGRI for support.";
        } else {
            action = "END";
            msg = "Invalid selection. Dial " + req.getServiceCode() + " again.";
        }
        ussdRepository.save(session);
        return Map.of("action", action, "message", cap182(msg));
    }

    private String cap182(String text) {
        return text.length() <= 182 ? text : text.substring(0, 182);
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(String ifMatch, UpdateNotificationPreferencesRequest req) {
        NotificationIdentityFilter.Identity id = NotificationIdentityFilter.current();
        String targetFarmerId;
        if (id.hasRole("FARMER") && !id.hasAnyRole("AGENT", "SUPPORT")) {
            targetFarmerId = id.userId();
        } else if (id.hasRole("AGENT")) {
            if (!"POLICY_ASSIST".equals(id.delegation())) {
                throw new NotificationApiException(HttpStatus.FORBIDDEN, "DELEGATION_REQUIRED", "POLICY_ASSIST delegation required");
            }
            if (id.stepUpLevel() == null || id.stepUpLevel().isBlank()) {
                throw new NotificationApiException(HttpStatus.FORBIDDEN, "STEP_UP_REQUIRED", "Farmer OTP step-up required");
            }
            targetFarmerId = req.getFarmerId() != null ? req.getFarmerId() : "frm_001";
        } else if (id.hasRole("SUPPORT")) {
            if (req.getSupportRequestRef() == null || req.getSupportRequestRef().isBlank()) {
                throw new NotificationApiException(HttpStatus.UNPROCESSABLE_CONTENT, "SUPPORT_REF_REQUIRED", "Support request reference required");
            }
            targetFarmerId = req.getFarmerId() != null ? req.getFarmerId() : "frm_001";
        } else {
            throw new NotificationApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient role");
        }

        if (req.getChannels() != null && req.getChannels().isEmpty()) {
            throw new NotificationApiException(HttpStatus.UNPROCESSABLE_CONTENT, "AT_LEAST_ONE_CHANNEL", "At least one channel must remain active");
        }
        if (req.getOptOut() != null) {
            for (String cat : req.getOptOut().keySet()) {
                if (MANDATORY_CATEGORIES.contains(cat.toUpperCase(Locale.ROOT))) {
                    throw new NotificationApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CATEGORY_NOT_OPTIONAL",
                            "Cannot opt out of mandatory category " + cat);
                }
            }
        }

        NotificationPreferenceEntity pref = preferenceRepository.findByFarmerId(targetFarmerId)
                .orElseGet(() -> {
                    NotificationPreferenceEntity def = support.getOrDefaultPreference(targetFarmerId, "sw");
                    def.setId(UUID.randomUUID());
                    return def;
                });

        if (ifMatch != null && !ifMatch.isBlank()) {
            String expected = "\"v" + pref.getVersion() + "\"";
            if (!expected.equals(ifMatch) && !String.valueOf(pref.getVersion()).equals(ifMatch)) {
                throw new NotificationApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Preference version mismatch");
            }
        }

        if (req.getLanguage() != null) pref.setLanguage(req.getLanguage());
        if (req.getChannels() != null) pref.setChannelsJson(support.toJson(req.getChannels()));
        if (req.getWhatsappOptIn() != null) pref.setWhatsappOptIn(req.getWhatsappOptIn());
        if (req.getOptOut() != null) pref.setOptOutJson(support.toJson(req.getOptOut()));
        if (req.getQuietHours() != null) pref.setQuietHoursJson(support.toJson(req.getQuietHours()));

        List<Map<String, Object>> history = support.fromJsonListMap(pref.getHistoryJson());
        history.add(Map.of("actor", id.userId(), "at", Instant.now().toString(), "action", "UPDATED"));
        pref.setHistoryJson(support.toJson(history));
        pref.setVersion(pref.getVersion() + 1);
        pref.setUpdatedBy(id.userId());
        pref.setUpdatedAt(Instant.now());
        pref = preferenceRepository.save(pref);

        support.emitOutbox("notification.preferences.changed", targetFarmerId,
                Map.of("farmerId", targetFarmerId, "language", pref.getLanguage(), "version", pref.getVersion()));

        return toPreferenceResponse(pref, false);
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getPreferences(String farmerId) {
        NotificationIdentityFilter.Identity id = NotificationIdentityFilter.current();
        String target = (id.hasRole("FARMER") && !id.hasAnyRole("AGENT", "SUPPORT", "ADMIN"))
                ? id.userId()
                : (farmerId != null ? farmerId : id.userId());
        Optional<NotificationPreferenceEntity> existing = preferenceRepository.findByFarmerId(target);
        boolean isDefault = existing.isEmpty();
        NotificationPreferenceEntity pref = existing.orElseGet(() -> support.getOrDefaultPreference(target, "sw"));
        return toPreferenceResponse(pref, isDefault);
    }

    private NotificationPreferencesResponse toPreferenceResponse(NotificationPreferenceEntity pref, boolean isDefault) {
        return NotificationPreferencesResponse.builder()
                .farmerId(pref.getFarmerId())
                .isDefault(isDefault)
                .language(pref.getLanguage())
                .channels(support.fromJsonListString(pref.getChannelsJson()))
                .optOut(support.fromJsonMap(pref.getOptOutJson()))
                .quietHours(support.fromJsonMap(pref.getQuietHoursJson()))
                .summary(List.of("Language: " + pref.getLanguage()))
                .consent(support.fromJsonListMap(pref.getConsentJson()))
                .history(support.fromJsonListMap(pref.getHistoryJson()))
                .version(pref.getVersion())
                .updatedAt(pref.getUpdatedAt().toString())
                .updatedBy(pref.getUpdatedBy())
                .build();
    }

    @Transactional
    public BroadcastResponse handleBroadcast(BroadcastAlertRequest req) {
        NotificationIdentityFilter.Identity id = NotificationIdentityFilter.current();
        if (!id.hasAnyRole("ALERT_PUBLISHER", "ADMIN")) {
            throw new NotificationApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Requires ALERT_PUBLISHER or ADMIN role");
        }
        String action = req.getAction() != null ? req.getAction().toUpperCase(Locale.ROOT) : "CREATE";

        if ("APPROVE".equals(action)) {
            BroadcastAlertEntity b = broadcastRepository.findByBroadcastId(req.getBroadcastId())
                    .orElseThrow(() -> new NotificationApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Broadcast not found"));
            if (id.userId().equals(b.getCreatedBy())) {
                throw new NotificationApiException(HttpStatus.FORBIDDEN, "APPROVER_NOT_INDEPENDENT", "Creator cannot self-approve broadcast");
            }
            if (req.getProposalHash() != null && !req.getProposalHash().equals(b.getProposalHash())) {
                throw new NotificationApiException(HttpStatus.CONFLICT, "PROPOSAL_CHANGED", "Broadcast proposal hash mismatch");
            }
            b.setApprovedBy(id.userId());
            b.setStatus("COMPLETED");
            dispatchBroadcastToAudience(b, support.fromJsonMap(b.getAudienceJson()), support.fromJsonMap(b.getVariablesJson()));
            b.setUpdatedAt(Instant.now());
            b = broadcastRepository.save(b);
            support.emitOutbox("notification.broadcast.completed", b.getBroadcastId(),
                    Map.of("broadcastId", b.getBroadcastId(), "sentCount", b.getSentCount()));
            return toBroadcastResponse(b);
        }

        if ("CANCEL".equals(action)) {
            BroadcastAlertEntity b = broadcastRepository.findByBroadcastId(req.getBroadcastId())
                    .orElseThrow(() -> new NotificationApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Broadcast not found"));
            b.setStatus("CANCELLED");
            b.setUpdatedAt(Instant.now());
            return toBroadcastResponse(broadcastRepository.save(b));
        }

        List<NotificationExternalClients.FarmerContact> matched = clients.resolveAudience(req.getAudience());
        long declaredCount = req.getAudience() != null && req.getAudience().get("estimatedCount") instanceof Number n
                ? n.longValue()
                : matched.size();
        BigDecimal estCost = BigDecimal.valueOf(declaredCount).multiply(BigDecimal.valueOf(0.85));

        if (req.isDryRun()) {
            return BroadcastResponse.builder()
                    .broadcastId("dry_run")
                    .status("DRY_RUN")
                    .alertType(req.getAlertType())
                    .severity(req.getSeverity())
                    .audience(Map.of("matchedCount", declaredCount))
                    .costEstimate(Map.of("currency", "KES", "amount", estCost))
                    .build();
        }

        String severity = req.getSeverity() != null ? req.getSeverity().toUpperCase(Locale.ROOT) : "INFO";
        boolean requiresSecondApprover = "CRITICAL".equals(severity)
                || declaredCount > properties.getRules().getBroadcastDualControlThreshold();

        String proposalHash = NotificationExternalClients.sha256(
                req.getTemplateCode() + ":" + severity + ":" + declaredCount);
        Instant now = Instant.now();
        BroadcastAlertEntity b = BroadcastAlertEntity.builder()
                .id(UUID.randomUUID())
                .broadcastId(support.idPrefix("brd_"))
                .alertType(req.getAlertType() != null ? req.getAlertType() : "GENERAL")
                .severity(severity)
                .audienceJson(support.toJson(req.getAudience() != null ? req.getAudience() : Map.of()))
                .templateCode(req.getTemplateCode())
                .actionsJson(support.toJson(req.getActions() != null ? req.getActions() : List.of()))
                .variablesJson(support.toJson(req.getVariables() != null ? req.getVariables() : Map.of()))
                .status(requiresSecondApprover ? "PENDING_APPROVAL" : "COMPLETED")
                .targetCount(declaredCount)
                .estimatedCost(estCost)
                .proposalHash(proposalHash)
                .createdBy(id.userId())
                .scheduleAt(req.getScheduleAt())
                .expiresAt(req.getExpiresAt())
                .createdAt(now)
                .updatedAt(now)
                .build();

        if (!requiresSecondApprover) {
            dispatchBroadcastToAudience(b, req.getAudience(), req.getVariables());
            b = broadcastRepository.save(b);
            support.emitOutbox("notification.broadcast.completed", b.getBroadcastId(),
                    Map.of("broadcastId", b.getBroadcastId(), "sentCount", b.getSentCount()));
        } else {
            b = broadcastRepository.save(b);
        }
        return toBroadcastResponse(b);
    }

    private void dispatchBroadcastToAudience(BroadcastAlertEntity b, Map<String, Object> audience, Map<String, Object> variables) {
        List<NotificationExternalClients.FarmerContact> targets = clients.resolveAudience(audience);
        long sent = 0;
        for (NotificationExternalClients.FarmerContact f : targets) {
            deliveryService.createAndDispatch("FARMER", f.getFarmerId(), b.getTemplateCode(), variables,
                    null, null, null, b.getBroadcastId(), null, null, b.getBroadcastId());
            sent++;
        }
        b.setSentCount(sent);
    }

    private BroadcastResponse toBroadcastResponse(BroadcastAlertEntity b) {
        return BroadcastResponse.builder()
                .broadcastId(b.getBroadcastId())
                .status(b.getStatus())
                .alertType(b.getAlertType())
                .severity(b.getSeverity())
                .audience(Map.of("targetCount", b.getTargetCount()))
                .approvalsRequired("PENDING_APPROVAL".equals(b.getStatus()) ? List.of("ALERT_PUBLISHER_SECOND") : List.of())
                .proposalHash(b.getProposalHash())
                .scheduleAt(b.getScheduleAt() != null ? b.getScheduleAt().toString() : null)
                .expiresAt(b.getExpiresAt() != null ? b.getExpiresAt().toString() : null)
                .costEstimate(Map.of("currency", "KES", "amount", b.getEstimatedCost()))
                .progress(Map.of("sent", b.getSentCount(), "delivered", b.getDeliveredCount(), "failed", b.getFailedCount()))
                .build();
    }

    @Transactional
    public Map<String, Object> processInboundMessage(String aggregator, String signature, String timestampHeader,
                                                     String nonce, Map<String, Object> body) {
        Instant ts = timestampHeader != null ? Instant.parse(timestampHeader) : null;
        String msisdn = String.valueOf(body.getOrDefault("msisdn", ""));
        String text = String.valueOf(body.getOrDefault("text", "")).trim();
        support.verifyHmacSignature(properties.getRules().getAggregatorHmacSecret(),
                (timestampHeader != null ? timestampHeader : "") + ":" + aggregator + ":" + msisdn, signature, ts);
        if (nonce == null || callbackInboxRepository.existsByNonce(nonce)) {
            throw new NotificationApiException(HttpStatus.CONFLICT, "CALLBACK_REPLAY", "Duplicate inbound nonce");
        }
        callbackInboxRepository.save(CallbackInboxEntity.builder()
                .id(UUID.randomUUID()).nonce(nonce).aggregator(aggregator).eventType("INBOUND").receivedAt(Instant.now()).build());

        String msisdnHash = NotificationExternalClients.sha256(msisdn);
        String upper = text.toUpperCase(Locale.ROOT);
        Instant now = Instant.now();

        if ("STOP".equals(upper) || "ACHA".equals(upper)) {
            suppressionRepository.save(SuppressionEntryEntity.builder()
                    .id(UUID.randomUUID()).msisdnHash(msisdnHash).channel("SMS")
                    .category("ALL").reason("STOP").active(true).createdAt(now).updatedAt(now).build());
            return Map.of("handled", true, "action", "OPTED_OUT");
        }
        if ("START".equals(upper) || "RESUME".equals(upper)) {
            for (SuppressionEntryEntity s : suppressionRepository.findByMsisdnHashAndActiveTrue(msisdnHash)) {
                s.setActive(false);
                s.setUpdatedAt(now);
                suppressionRepository.save(s);
            }
            return Map.of("handled", true, "action", "RESUMED");
        }
        if (upper.startsWith("LANG ")) {
            String lang = text.substring(5).trim().toLowerCase(Locale.ROOT);
            clients.findFarmerByMsisdnHash(msisdnHash).ifPresent(f -> {
                NotificationPreferenceEntity pref = preferenceRepository.findByFarmerId(f.getFarmerId())
                        .orElseGet(() -> {
                            NotificationPreferenceEntity def = support.getOrDefaultPreference(f.getFarmerId(), lang);
                            def.setId(UUID.randomUUID());
                            return def;
                        });
                pref.setLanguage(lang);
                pref.setVersion(pref.getVersion() + 1);
                pref.setUpdatedAt(now);
                preferenceRepository.save(pref);
            });
            return Map.of("handled", true, "action", "LANGUAGE_UPDATED", "language", lang);
        }
        return Map.of("handled", true, "action", "HELP_SENT");
    }

    @Transactional
    public TemplateResponse manageTemplate(String code, String language, ManageTemplateRequest req) {
        NotificationIdentityFilter.Identity id = NotificationIdentityFilter.current();
        String action = req.getAction() != null ? req.getAction().toUpperCase(Locale.ROOT) : "SAVE";
        Instant now = Instant.now();

        if ("SAVE".equals(action) || "SUBMIT".equals(action) || "PREVIEW".equals(action)) {
            if (!id.hasAnyRole("TEMPLATE_EDITOR", "ADMIN") && !"PREVIEW".equals(action)) {
                throw new NotificationApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Requires TEMPLATE_EDITOR role");
            }
            NotificationCategory cat = NotificationCategory.valueOf(
                    req.getCategory() != null ? req.getCategory().toUpperCase(Locale.ROOT) : "SERVICE");
            Map<String, String> variants = req.getVariants() != null ? req.getVariants() : Map.of();
            validateTemplateContent(cat, variants);

            if ("PREVIEW".equals(action)) {
                return TemplateResponse.builder()
                        .code(code).language(language).version(req.getVersion())
                        .status("PREVIEW").category(cat.name())
                        .mandatory(req.isMandatory()).acceptsSecret(req.isAcceptsSecret())
                        .preview(Map.copyOf(variants))
                        .checks(List.of(Map.of("check", "CONTENT", "passed", true)))
                        .build();
            }

            Optional<MessageTemplateEntity> existing = templateRepository.findByCodeAndLanguageAndVersion(code, language, req.getVersion());
            MessageTemplateEntity entity = existing.orElseGet(() -> MessageTemplateEntity.builder()
                    .id(UUID.randomUUID())
                    .code(code).language(language).version(req.getVersion())
                    .createdAt(now)
                    .build());
            entity.setCategory(cat);
            entity.setMandatory(req.isMandatory());
            entity.setAcceptsSecret(req.isAcceptsSecret());
            entity.setVariantsJson(support.toJson(variants));
            entity.setVariablesJson(support.toJson(req.getVariables() != null ? req.getVariables() : List.of()));
            entity.setChecksJson(support.toJson(List.of(Map.of("check", "CONTENT", "passed", true))));
            entity.setStatus("SUBMIT".equals(action) ? TemplateStatus.IN_REVIEW : TemplateStatus.DRAFT);
            entity.setLastEditedBy(id.userId());
            entity.setNote(req.getNote());
            entity.setUpdatedAt(now);
            entity = templateRepository.save(entity);
            return toTemplateResponse(entity);
        }

        MessageTemplateEntity entity = templateRepository.findByCodeAndLanguageAndVersion(code, language, req.getVersion())
                .orElseThrow(() -> new NotificationApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Template version not found"));

        if ("APPROVE".equals(action)) {
            if (!id.hasRole("TEMPLATE_APPROVER") && !(entity.isMandatory() && id.hasRole("COMPLIANCE_OFFICER"))) {
                throw new NotificationApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Requires TEMPLATE_APPROVER (or COMPLIANCE_OFFICER for mandatory)");
            }
            if (entity.isMandatory() && !id.hasRole("COMPLIANCE_OFFICER")) {
                throw new NotificationApiException(HttpStatus.FORBIDDEN, "COMPLIANCE_APPROVAL_REQUIRED", "Mandatory templates require COMPLIANCE_OFFICER");
            }
            if (id.userId().equals(entity.getLastEditedBy())) {
                throw new NotificationApiException(HttpStatus.FORBIDDEN, "APPROVER_NOT_INDEPENDENT", "Editor cannot approve own template");
            }
            for (MessageTemplateEntity prev : templateRepository.findByCodeAndLanguage(code, language)) {
                if (prev.isCurrentVersion()) {
                    prev.setCurrentVersion(false);
                    templateRepository.save(prev);
                }
            }
            entity.setStatus(TemplateStatus.APPROVED);
            entity.setApprovedBy(id.userId());
            entity.setProviderStatus("APPROVED");
            entity.setCurrentVersion(true);
            entity.setUpdatedAt(now);
            return toTemplateResponse(templateRepository.save(entity));
        }

        if ("REJECT".equals(action)) {
            entity.setStatus(TemplateStatus.REJECTED);
            entity.setNote(req.getNote());
            entity.setUpdatedAt(now);
            return toTemplateResponse(templateRepository.save(entity));
        }

        if ("RETIRE".equals(action)) {
            entity.setStatus(TemplateStatus.RETIRED);
            entity.setCurrentVersion(false);
            entity.setUpdatedAt(now);
            return toTemplateResponse(templateRepository.save(entity));
        }

        throw new NotificationApiException(HttpStatus.BAD_REQUEST, "INVALID_ACTION", "Unsupported template action");
    }

    private void validateTemplateContent(NotificationCategory category, Map<String, String> variants) {
        String sms = variants.get("SMS");
        if (sms != null) {
            if (sms.length() > properties.getRules().getMaxSmsSegments() * 153) {
                throw new NotificationApiException(HttpStatus.UNPROCESSABLE_CONTENT, "SEGMENT_LIMIT_EXCEEDED",
                        "SMS exceeds maximum allowed segments");
            }
            if (sms.contains("http://") || (sms.contains("https://") && !sms.contains("agreeshield.org"))) {
                throw new NotificationApiException(HttpStatus.UNPROCESSABLE_CONTENT, "LINK_NOT_ALLOWED",
                        "Unapproved URL in template");
            }
            if ((category == NotificationCategory.ADVISORY || category == NotificationCategory.PROMOTIONAL)
                    && !sms.toUpperCase(Locale.ROOT).contains("STOP") && !sms.toUpperCase(Locale.ROOT).contains("ACHA")) {
                throw new NotificationApiException(HttpStatus.UNPROCESSABLE_CONTENT, "STOP_TEXT_REQUIRED",
                        "Advisory and promotional templates must include STOP/ACHA text");
            }
        }
    }

    private TemplateResponse toTemplateResponse(MessageTemplateEntity t) {
        return TemplateResponse.builder()
                .code(t.getCode())
                .language(t.getLanguage())
                .version(t.getVersion())
                .status(t.getStatus().name())
                .category(t.getCategory().name())
                .mandatory(t.isMandatory())
                .acceptsSecret(t.isAcceptsSecret())
                .checks(support.fromJsonListMap(t.getChecksJson()))
                .approvals(support.fromJsonListMap(t.getApprovalsJson()))
                .providerStatus(t.getProviderStatus())
                .current(t.isCurrentVersion())
                .build();
    }
}
