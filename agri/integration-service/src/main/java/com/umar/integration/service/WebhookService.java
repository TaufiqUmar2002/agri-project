package com.umar.integration.service;

import com.umar.integration.client.DataVaultClient;
import com.umar.integration.client.NotificationClient;
import com.umar.integration.client.WebhookHttpClient;
import com.umar.integration.config.HubProperties;
import com.umar.integration.domain.entity.*;
import com.umar.integration.domain.enums.*;
import com.umar.enums.integration.*;
import com.umar.payload.request.integration.*;
import com.umar.integration.error.HubException;
import com.umar.integration.repository.*;
import com.umar.integration.security.EgressValidator;
import com.umar.integration.security.HubAccessGuard;
import com.umar.integration.security.HubIdentity;
import com.umar.integration.service.support.*;
import com.umar.integration.util.CryptoUtils;
import com.umar.integration.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final int[] RETRY_BACKOFF_SECONDS = {30, 120, 600, 3600, 21600, 86400};

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final DomainEventArchiveRepository archiveRepository;
    private final ReplayJobRepository replayJobRepository;
    private final SubscriptionTestRecordRepository testRecordRepository;
    private final TenantMapRepository tenantMapRepository;
    private final PartnerRepository partnerRepository;
    private final ApprovalRepository approvalRepository;
    private final DataVaultClient dataVaultClient;
    private final WebhookHttpClient webhookHttpClient;
    private final EgressValidator egressValidator;
    private final PlatformAllowList allowList;
    private final ExtensionService extensionService;
    private final NotificationClient notificationClient;
    private final HubAccessGuard accessGuard;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final RateLimiterService rateLimiterService;
    private final HubProperties properties;
    private final JsonUtils jsonUtils;

    /**
     * INT-01: Register Webhook Subscription.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> registerSubscription(String idempotencyKey, RegisterSubscriptionRequest request) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN);

        Optional<Map> cached = idempotencyService.findExisting("REGISTER_SUBSCRIPTION", idempotencyKey, request, Map.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        String partnerId = accessGuard.resolveEffectivePartnerId(identity, request.getPartnerId());
        if (partnerId == null || partnerId.isBlank()) {
            throw HubException.validation("partnerId is required");
        }

        PartnerEntity partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> HubException.forbidden("Not authorised"));
        if (partner.getStatus() != PartnerStatus.ACTIVE) {
            throw HubException.forbidden("Not authorised");
        }

        rateLimiterService.checkAndConsume("sub-reg:" + partnerId, 20, Duration.ofDays(1),
                "Daily subscription registration limit reached");

        // Check partner subscription count quota
        Map<String, Object> partnerQuotas = jsonUtils.toMap(partner.getQuotasJson());
        int maxSubs = partnerQuotas.get("subscriptions") instanceof Number n
                ? n.intValue() : properties.getQuotas().getMaxSubscriptionsPerPartner();
        long currentSubs = subscriptionRepository.countByPartnerIdAndStatusNot(partnerId, SubscriptionStatus.DELETED);
        if (currentSubs >= maxSubs) {
            throw HubException.unprocessable("QUOTA_EXCEEDED", "Your subscription quota is used up");
        }

        // Check duplicate name
        Optional<WebhookSubscriptionEntity> dupName = subscriptionRepository
                .findByPartnerIdAndNameIgnoreCase(partnerId, request.getName().trim());
        if (dupName.isPresent() && dupName.get().getStatus() != SubscriptionStatus.DELETED) {
            throw HubException.conflict("SUBSCRIPTION_EXISTS", "A matching subscription already exists",
                    Map.of("subscriptionId", dupName.get().getSubscriptionId()));
        }

        // Validate event types against partner's allow-list
        List<String> partnerAllowedEvents = jsonUtils.toStringList(partner.getAllowedEventTypesJson());
        List<String> disallowed = request.getEventTypes().stream()
                .filter(e -> !partnerAllowedEvents.contains(e) || !allowList.isEventExportable(e))
                .toList();
        if (!disallowed.isEmpty()) {
            throw HubException.unprocessable("EVENT_TYPE_NOT_ALLOWED",
                    "This event type is not available to you", Map.of("eventTypes", disallowed));
        }

        // Validate filters within partner's visibility scope
        validateFiltersWithinPartnerScope(partner, request.getFilters());

        // Validate dataScope <= partner grant
        DataScopeLevel effectiveScope = request.getDataScope() != null ? request.getDataScope() : DataScopeLevel.PARTNER_DEFAULT;
        if (effectiveScope.ordinal() > partner.getDataScope().ordinal()) {
            throw HubException.unprocessable("FILTER_OUT_OF_SCOPE", "Requested dataScope exceeds partner grant");
        }

        int maxRate = partnerQuotas.get("webhookRatePerSecond") instanceof Number n
                ? n.intValue() : properties.getWebhook().getDefaultRateLimitPerSecond();
        int rateLimit = request.getRateLimitPerSecond() != null
                ? request.getRateLimitPerSecond()
                : Math.min(properties.getWebhook().getDefaultRateLimitPerSecond(), maxRate);
        if (rateLimit > maxRate) {
            throw HubException.unprocessable("QUOTA_EXCEEDED", "Requested rateLimitPerSecond exceeds partner quota");
        }

        // Validate targetUrl (SSRF checks)
        egressValidator.validatePublicHttpsUrl(request.getTargetUrl(), "TARGET_NOT_PUBLIC",
                "The target address is not allowed");

        String subscriptionId = "sub_" + CryptoUtils.randomHex(4);
        DataVaultClient.GeneratedKey generatedKey = dataVaultClient.generateWebhookSigningKey(subscriptionId, partnerId);

        // Send signed ownership challenge
        ChallengeOutcome challenge = sendOwnershipChallenge(request.getTargetUrl(), generatedKey.keyId());
        Instant now = Instant.now();

        SubscriptionStatus status;
        String approvalId = null;
        if (!challenge.passed()) {
            status = SubscriptionStatus.PENDING_VERIFICATION;
        } else if (request.getEnvironment() == HubEnvironment.PRODUCTION) {
            status = SubscriptionStatus.PENDING_APPROVAL;
            approvalId = "apr_" + CryptoUtils.randomHex(4);
        } else {
            status = SubscriptionStatus.ACTIVE;
        }

        String configHash = CryptoUtils.sha256Hex(jsonUtils.toJson(request));
        WebhookSubscriptionEntity sub = subscriptionRepository.save(WebhookSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .partnerId(partnerId)
                .name(request.getName().trim())
                .environment(request.getEnvironment())
                .targetUrl(request.getTargetUrl().trim())
                .eventTypesJson(jsonUtils.toJson(request.getEventTypes()))
                .filtersJson(jsonUtils.toJson(request.getFilters()))
                .format(request.getFormat() != null ? request.getFormat() : WebhookFormat.JSON)
                .dataScope(effectiveScope)
                .ordering(request.getOrdering() != null ? request.getOrdering() : DeliveryOrdering.NONE)
                .rateLimitPerSecond(rateLimit)
                .batchSize(request.getBatchSize() != null ? request.getBatchSize() : 1)
                .contactJson(jsonUtils.toJson(request.getContact()))
                .status(status)
                .keyId(generatedKey.keyId())
                .keyCreatedAt(generatedKey.createdAt())
                .challengeResult(challenge.passed() ? "PASSED" : "FAILED")
                .challengeCheckedAt(now)
                .challengeFailureReason(challenge.failureReason())
                .approvalId(approvalId)
                .securityBlocked(false)
                .version(1)
                .createdBy(identity.actorId())
                .createdAt(now)
                .updatedAt(now)
                .build());

        if (approvalId != null) {
            approvalRepository.save(ApprovalEntity.builder()
                    .approvalId(approvalId)
                    .type(ApprovalType.SUBSCRIPTION_PRODUCTION)
                    .subjectId(subscriptionId)
                    .subjectVersion(1)
                    .subjectHash(configHash)
                    .requiredRolesJson(jsonUtils.toJson(List.of(HubRole.INTEGRATION_MANAGER.name())))
                    .decisionsJson("[]")
                    .status(ApprovalStatus.OPEN)
                    .requestedBy(identity.actorId())
                    .slaDueAt(now.plus(Duration.ofDays(1)))
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        outboxService.publishIntegrationEvent("integration.subscription.changed", subscriptionId, Map.of(
                "subscriptionId", subscriptionId,
                "partnerId", partnerId,
                "change", "CREATED",
                "environment", sub.getEnvironment().name(),
                "eventTypes", request.getEventTypes()
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("subscriptionId", subscriptionId);
        response.put("name", sub.getName());
        response.put("environment", sub.getEnvironment().name());
        response.put("status", sub.getStatus().name());
        response.put("eventTypes", request.getEventTypes());
        if (request.getFilters() != null) response.put("filters", request.getFilters());
        response.put("format", sub.getFormat().name());
        response.put("ordering", sub.getOrdering().name());
        response.put("signing", Map.of(
                "keyId", generatedKey.keyId(),
                "algorithm", "HMAC_SHA256",
                "headers", List.of("X-AgriShield-Signature", "X-AgriShield-Timestamp"),
                "secret", generatedKey.revealOnceSecret()
        ));
        Map<String, Object> verification = new LinkedHashMap<>();
        verification.put("challengeResult", sub.getChallengeResult());
        verification.put("checkedAt", now.toString());
        if (sub.getChallengeFailureReason() != null) {
            verification.put("failureReason", sub.getChallengeFailureReason());
        }
        response.put("verification", verification);
        if (approvalId != null) {
            response.put("approvalId", approvalId);
            response.put("subjectHash", configHash);
        }
        response.put("limits", Map.of(
                "rateLimitPerSecond", rateLimit,
                "maxPayloadBytes", properties.getWebhook().getMaxPayloadBytes(),
                "retryWindowHours", properties.getWebhook().getRetryWindowHours()
        ));
        response.put("createdAt", now.toString());

        idempotencyService.save("REGISTER_SUBSCRIPTION", idempotencyKey, identity.actorId(),
                request, HttpStatus.CREATED.value(), response, Duration.ofHours(24));
        return response;
    }

    /**
     * INT-02 (GET): Get Subscription with health, recent deliveries, key metadata, and backlog.
     */
    @Transactional(readOnly = true)
    public SubscriptionView getSubscription(String id, String include, String window) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN, HubRole.AUDITOR);

        WebhookSubscriptionEntity sub = loadAccessibleSubscription(id, identity);
        rateLimiterService.checkAndConsume("sub-get:" + sub.getPartnerId(), 120, Duration.ofMinutes(1),
                "Subscription read rate limit exceeded");

        Duration win = switch (window == null ? "DAY" : window.trim().toUpperCase(Locale.ROOT)) {
            case "HOUR" -> Duration.ofHours(1);
            case "WEEK" -> Duration.ofDays(7);
            default -> Duration.ofDays(1);
        };
        Set<String> includes = new HashSet<>();
        for (String p : (include == null ? "health" : include).split(",")) {
            includes.add(p.trim().toLowerCase(Locale.ROOT));
        }

        Map<String, Object> body = toSubscriptionResponse(sub, null);
        Instant now = Instant.now();
        List<WebhookDeliveryEntity> windowDeliveries = deliveryRepository
                .findBySubscriptionIdAndCreatedAtBetween(id, now.minus(win), now);

        if (includes.contains("health") || includes.contains("backlog")) {
            body.put("health", computeHealth(sub, windowDeliveries));
        }
        if (includes.contains("deliveries")) {
            List<Map<String, Object>> recent = deliveryRepository
                    .findTop50BySubscriptionIdOrderByCreatedAtDesc(id).stream()
                    .map(d -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("deliveryId", d.getDeliveryId());
                        m.put("eventType", d.getEventType());
                        m.put("status", d.getStatus().name());
                        if (d.getResponseCode() != null) m.put("responseCode", d.getResponseCode());
                        m.put("attempts", d.getAttempts());
                        m.put("at", d.getCreatedAt().toString());
                        return m;
                    }).toList();
            body.put("deliveries", recent);
        }
        if (includes.contains("key")) {
            Map<String, Object> keyInfo = new LinkedHashMap<>();
            keyInfo.put("keyId", sub.getKeyId());
            if (sub.getKeyCreatedAt() != null) keyInfo.put("createdAt", sub.getKeyCreatedAt().toString());
            if (sub.getPreviousKeyId() != null) keyInfo.put("previousKeyId", sub.getPreviousKeyId());
            if (sub.getOverlapEndsAt() != null) keyInfo.put("overlapEndsAt", sub.getOverlapEndsAt().toString());
            body.put("key", keyInfo);
        }

        return new SubscriptionView(body, sub.getVersion());
    }

    /**
     * INT-02 (PUT): Pause, resume, or rotate key.
     */
    @Transactional
    public SubscriptionView updateSubscription(String id, String ifMatch, UpdateSubscriptionRequest request) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN);

        WebhookSubscriptionEntity sub = loadAccessibleSubscription(id, identity);
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new HubException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "If-Match header is required");
        }
        int expectedVersion;
        try {
            expectedVersion = Integer.parseInt(ifMatch.replace("\"", "").trim());
        } catch (NumberFormatException e) {
            throw HubException.validation("Invalid If-Match ETag");
        }
        if (sub.getVersion() != expectedVersion) {
            throw new HubException(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT",
                    "The subscription changed. Reload and retry");
        }

        rateLimiterService.checkAndConsume("sub-put:" + sub.getPartnerId(), 20, Duration.ofHours(1),
                "Subscription update rate limit exceeded");

        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        String newSecret = null;

        if ("SET_STATUS".equals(action)) {
            if (request.getStatus() == null || request.getStatus().isBlank()) {
                throw HubException.validation("status is required for SET_STATUS");
            }
            SubscriptionStatus targetStatus;
            try {
                targetStatus = SubscriptionStatus.valueOf(request.getStatus().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw HubException.validation("status must be PAUSED or ACTIVE");
            }
            if (targetStatus != SubscriptionStatus.PAUSED && targetStatus != SubscriptionStatus.ACTIVE) {
                throw HubException.validation("status must be PAUSED or ACTIVE");
            }
            if (sub.getStatus() == SubscriptionStatus.DELETED
                    || sub.getStatus() == SubscriptionStatus.PENDING_APPROVAL
                    || sub.getStatus() == SubscriptionStatus.PENDING_VERIFICATION) {
                throw HubException.conflict("INVALID_STATE",
                        "This change is not possible in the current state", Map.of("status", sub.getStatus().name()));
            }
            if (sub.getStatus() != targetStatus) {
                sub.setStatus(targetStatus);
                sub.setPauseReason(targetStatus == SubscriptionStatus.PAUSED ? PauseReason.MANUAL : null);
                sub.setVersion(sub.getVersion() + 1);
                sub.setUpdatedAt(now);
                subscriptionRepository.save(sub);

                outboxService.publishIntegrationEvent("integration.subscription.changed", sub.getSubscriptionId(), Map.of(
                        "subscriptionId", sub.getSubscriptionId(),
                        "partnerId", sub.getPartnerId(),
                        "change", targetStatus == SubscriptionStatus.PAUSED ? "PAUSED" : "RESUMED",
                        "reason", request.getReason(),
                        "environment", sub.getEnvironment().name()
                ));
            }
        } else if ("ROTATE_KEY".equals(action)) {
            if (sub.getStatus() == SubscriptionStatus.DELETED) {
                throw HubException.conflict("INVALID_STATE",
                        "This change is not possible in the current state", Map.of("status", "DELETED"));
            }
            if (sub.getOverlapEndsAt() != null && sub.getOverlapEndsAt().isAfter(now)) {
                throw HubException.conflict("INVALID_STATE",
                        "A key rotation overlap window is already active", Map.of("overlapEndsAt", sub.getOverlapEndsAt().toString()));
            }
            DataVaultClient.GeneratedKey replacement = dataVaultClient.generateWebhookSigningKey(
                    sub.getSubscriptionId(), sub.getPartnerId());
            sub.setPreviousKeyId(sub.getKeyId());
            sub.setKeyId(replacement.keyId());
            sub.setKeyCreatedAt(replacement.createdAt());
            sub.setOverlapEndsAt(now.plus(Duration.ofHours(properties.getWebhook().getKeyOverlapHours())));
            sub.setVersion(sub.getVersion() + 1);
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
            newSecret = replacement.revealOnceSecret();

            outboxService.publishIntegrationEvent("integration.subscription.changed", sub.getSubscriptionId(), Map.of(
                    "subscriptionId", sub.getSubscriptionId(),
                    "partnerId", sub.getPartnerId(),
                    "change", "KEY_ROTATED",
                    "reason", request.getReason(),
                    "environment", sub.getEnvironment().name()
            ));
        } else {
            throw HubException.validation("action must be SET_STATUS or ROTATE_KEY");
        }

        return new SubscriptionView(toSubscriptionResponse(sub, newSecret), sub.getVersion());
    }

    /**
     * INT-02 (DELETE): End subscription.
     */
    @Transactional
    public void deleteSubscription(String id, Boolean confirm) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN);

        if (!Boolean.TRUE.equals(confirm)) {
            throw HubException.validation("confirm=true query parameter is required to delete a subscription");
        }
        WebhookSubscriptionEntity sub = loadAccessibleSubscription(id, identity);
        if (sub.getStatus() == SubscriptionStatus.DELETED) {
            return; // Idempotent 204
        }
        sub.setStatus(SubscriptionStatus.DELETED);
        sub.setVersion(sub.getVersion() + 1);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);

        outboxService.publishIntegrationEvent("integration.subscription.changed", sub.getSubscriptionId(), Map.of(
                "subscriptionId", sub.getSubscriptionId(),
                "partnerId", sub.getPartnerId(),
                "change", "DELETED",
                "environment", sub.getEnvironment().name()
        ));
    }

    /**
     * INT-04: Send Test Delivery.
     */
    @Transactional
    public Map<String, Object> sendTestDelivery(String id, TestDeliveryRequest request) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN);

        WebhookSubscriptionEntity sub = loadAccessibleSubscription(id, identity);
        if (sub.getStatus() == SubscriptionStatus.DELETED
                || sub.getStatus() == SubscriptionStatus.PENDING_VERIFICATION
                || sub.isSecurityBlocked()) {
            throw HubException.conflict("SUBSCRIPTION_NOT_TESTABLE", "This subscription cannot be tested yet");
        }

        List<String> subscribedTypes = jsonUtils.toStringList(sub.getEventTypesJson());
        if (!subscribedTypes.contains(request.getEventType())) {
            throw HubException.validation("eventType must be one of the subscription's eventTypes");
        }
        String variant = request.getVariant() != null ? request.getVariant().toUpperCase(Locale.ROOT) : "TYPICAL";
        if (!Set.of("TYPICAL", "MINIMAL", "LARGE").contains(variant)) {
            throw HubException.validation("variant must be TYPICAL, MINIMAL, or LARGE");
        }
        String simFailure = request.getSimulateFailure() != null ? request.getSimulateFailure().toUpperCase(Locale.ROOT) : "NONE";
        if (!Set.of("NONE", "SLOW", "ERROR_500").contains(simFailure)) {
            throw HubException.validation("simulateFailure must be NONE, SLOW, or ERROR_500");
        }
        if (!"NONE".equals(simFailure) && sub.getEnvironment() != HubEnvironment.SANDBOX) {
            throw HubException.validation("simulateFailure is supported in SANDBOX only");
        }

        long testsLastHour = testRecordRepository.countBySubscriptionIdAndTestedAtAfter(
                sub.getSubscriptionId(), Instant.now().minus(Duration.ofHours(1)));
        if (testsLastHour >= properties.getWebhook().getTestRateLimitPerHour()) {
            throw HubException.rateLimited(3600L, "Too many test deliveries; limit is 10 per hour");
        }

        egressValidator.validatePublicHttpsUrl(sub.getTargetUrl(), "TARGET_NOT_PUBLIC",
                "The target address is not allowed");

        String testId = "tst_" + CryptoUtils.randomHex(3);
        String eventId = "evt_test_" + CryptoUtils.randomHex(4);
        String deliveryId = "dlv_test_" + CryptoUtils.randomHex(4);
        Instant now = Instant.now();

        Map<String, Object> syntheticData = new LinkedHashMap<>();
        syntheticData.put("policyRef", "p_test_0000000000000000");
        syntheticData.put("externalRef", "TEST-REF-0001");
        if (!"MINIMAL".equals(variant)) {
            syntheticData.put("amountMinor", 150000);
            syntheticData.put("currency", "KES");
            syntheticData.put("fullyPaid", true);
        }
        if ("LARGE".equals(variant)) {
            syntheticData.put("padding", "x".repeat(4096));
        }

        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("id", eventId);
        bodyMap.put("type", request.getEventType());
        bodyMap.put("test", true);
        bodyMap.put("occurredAt", now.toString());
        bodyMap.put("data", syntheticData);
        String bodyJson = jsonUtils.toJson(bodyMap);

        String ts = String.valueOf(now.getEpochSecond());
        String sigHex = dataVaultClient.signPayload(sub.getKeyId(), ts, bodyJson);
        String signatureHeader = "t=" + ts + ",v1=" + sigHex;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-AgriShield-Event-Id", eventId);
        headers.put("X-AgriShield-Delivery-Id", deliveryId);
        headers.put("X-AgriShield-Attempt", "1");
        headers.put("X-AgriShield-Test", "true");
        headers.put("X-AgriShield-Timestamp", ts);
        headers.put("X-AgriShield-Signature", signatureHeader);

        WebhookHttpClient.HttpCallResult callResult;
        if ("SLOW".equals(simFailure)) {
            callResult = new WebhookHttpClient.HttpCallResult("TIMEOUT", null, 10000L, null, null, null);
        } else if ("ERROR_500".equals(simFailure)) {
            callResult = new WebhookHttpClient.HttpCallResult("RECEIVER_ERROR", 500, 12L, "Simulated 500", null, null);
        } else {
            callResult = webhookHttpClient.post(sub.getTargetUrl(), headers, bodyJson,
                    properties.getWebhook().getDeliveryTimeoutMs());
        }

        List<String> advice = new ArrayList<>();
        if ("TIMEOUT".equals(callResult.outcome())) {
            advice.add("Receiver timed out after 10 seconds; acknowledge receipt quickly and process asynchronously.");
        } else if ("REDIRECT_NOT_FOLLOWED".equals(callResult.outcome())) {
            advice.add("Redirects are not followed; configure the final HTTPS target URL directly.");
        } else if (callResult.statusCode() != null && (callResult.statusCode() == 401 || callResult.statusCode() == 403)) {
            advice.add("Verify HMAC-SHA256 over '<timestamp>.<rawBody>' and ensure clock skew is within 5 minutes.");
        } else if (!callResult.is2xx()) {
            advice.add("Endpoint must return an HTTP 2xx status code within 10 seconds.");
        }

        testRecordRepository.save(SubscriptionTestRecordEntity.builder()
                .testId(testId)
                .subscriptionId(sub.getSubscriptionId())
                .eventType(request.getEventType())
                .result(callResult.outcome())
                .responseCode(callResult.statusCode())
                .latencyMs(callResult.latencyMs())
                .responseSnippet(callResult.responseSnippet())
                .testedAt(now)
                .build());

        Map<String, String> maskedSentHeaders = new LinkedHashMap<>(headers);
        maskedSentHeaders.put("X-AgriShield-Signature", "t=" + ts + ",v1=***MASKED***");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("testId", testId);
        response.put("subscriptionId", sub.getSubscriptionId());
        response.put("eventType", request.getEventType());
        response.put("result", callResult.outcome());
        if (callResult.statusCode() != null) response.put("responseCode", callResult.statusCode());
        response.put("latencyMs", callResult.latencyMs());
        if (callResult.responseSnippet() != null) response.put("responseSnippet", callResult.responseSnippet());
        response.put("sent", Map.of("headers", maskedSentHeaders, "body", bodyMap));
        response.put("advice", advice);
        response.put("testedAt", now.toString());
        return response;
    }

    /**
     * INT-05: Replay Deliveries.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> replayDeliveries(String idOrRange, String idempotencyKey, ReplayDeliveriesRequest request) {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN);

        Optional<Map> cached = idempotencyService.findExisting("REPLAY:" + idOrRange, idempotencyKey, request, Map.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        boolean isRange = "range".equalsIgnoreCase(idOrRange);
        WebhookSubscriptionEntity sub;
        ReplayMode mode;
        List<DomainEventArchiveEntity> eventsToReplay = new ArrayList<>();
        String targetDeliveryId = null;
        Instant fromInstant = null;
        Instant toInstant = null;

        if (!isRange) {
            WebhookDeliveryEntity dlv = deliveryRepository.findById(idOrRange)
                    .orElseThrow(() -> HubException.notFound("DELIVERY_NOT_FOUND", "Delivery not found"));
            sub = loadAccessibleSubscription(dlv.getSubscriptionId(), identity);
            mode = ReplayMode.DELIVERY;
            targetDeliveryId = dlv.getDeliveryId();
            Instant retentionCutoff = Instant.now().minus(Duration.ofDays(properties.getWebhook().getReplayRetentionDays()));
            if (dlv.getCreatedAt().isBefore(retentionCutoff)) {
                throw HubException.unprocessable("OUTSIDE_RETENTION",
                        "These events are no longer available for replay");
            }
            DomainEventArchiveEntity archived = archiveRepository.findById(dlv.getEventId()).orElse(null);
            if (archived != null) {
                eventsToReplay.add(archived);
            }
        } else {
            if (request.getSubscriptionId() == null || request.getSubscriptionId().isBlank()) {
                throw HubException.validation("subscriptionId is required for range replay");
            }
            sub = loadAccessibleSubscription(request.getSubscriptionId(), identity);

            boolean hasTime = request.getFrom() != null && request.getTo() != null;
            boolean hasOffset = request.getFromOffset() != null && request.getToOffset() != null;
            if (hasTime == hasOffset) {
                throw HubException.validation("Provide either (from, to) or (fromOffset, toOffset)");
            }
            if (hasTime) {
                mode = ReplayMode.TIME_RANGE;
                fromInstant = Instant.parse(request.getFrom());
                toInstant = Instant.parse(request.getTo());
                if (toInstant.isBefore(fromInstant)) {
                    throw HubException.validation("to must be after from");
                }
                if (Duration.between(fromInstant, toInstant).toHours() > properties.getWebhook().getReplayMaxHours()) {
                    throw HubException.unprocessable("RANGE_TOO_LARGE", "Choose a smaller range");
                }
                Instant retentionCutoff = Instant.now().minus(Duration.ofDays(properties.getWebhook().getReplayRetentionDays()));
                if (fromInstant.isBefore(retentionCutoff)) {
                    throw HubException.unprocessable("OUTSIDE_RETENTION",
                            "These events are no longer available for replay");
                }
                eventsToReplay = archiveRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(fromInstant, toInstant);
            } else {
                mode = ReplayMode.OFFSET_RANGE;
                if (!Objects.equals(request.getFromOffset().getPartition(), request.getToOffset().getPartition())) {
                    throw HubException.validation("fromOffset and toOffset must reference the same partition");
                }
                long span = request.getToOffset().getOffset() - request.getFromOffset().getOffset() + 1;
                if (span < 1 || span > properties.getWebhook().getReplayMaxEvents()) {
                    throw HubException.unprocessable("RANGE_TOO_LARGE", "Choose a smaller range");
                }
                eventsToReplay = archiveRepository.findByKafkaPartitionAndKafkaOffsetBetweenOrderByKafkaOffsetAsc(
                        request.getFromOffset().getPartition(),
                        request.getFromOffset().getOffset(),
                        request.getToOffset().getOffset());
            }
        }

        if (sub.getStatus() != SubscriptionStatus.ACTIVE || sub.isSecurityBlocked()) {
            throw HubException.conflict("SUBSCRIPTION_NOT_ACTIVE", "The subscription must be active to replay");
        }

        Optional<ReplayJobEntity> activeJob = replayJobRepository.findFirstBySubscriptionIdAndStatusIn(
                sub.getSubscriptionId(), List.of(JobStatus.QUEUED, JobStatus.RUNNING));
        if (activeJob.isPresent()) {
            throw HubException.conflict("REPLAY_IN_PROGRESS", "A replay is already running",
                    Map.of("jobId", activeJob.get().getJobId()));
        }

        long jobsToday = replayJobRepository.countBySubscriptionIdAndCreatedAtAfter(
                sub.getSubscriptionId(), Instant.now().minus(Duration.ofDays(1)));
        if (jobsToday >= properties.getWebhook().getReplayJobsPerDay()) {
            throw HubException.rateLimited(86400L, "Replay daily limit reached");
        }

        if (eventsToReplay.size() > properties.getWebhook().getReplayMaxEvents()) {
            throw HubException.unprocessable("RANGE_TOO_LARGE", "Choose a smaller range");
        }

        String jobId = "rpl_" + CryptoUtils.randomHex(3);
        int halfRate = Math.max(1, sub.getRateLimitPerSecond() / 2);
        int estDuration = Math.max(1, (int) Math.ceil((double) eventsToReplay.size() / halfRate));
        Instant now = Instant.now();

        ReplayJobEntity job = replayJobRepository.save(ReplayJobEntity.builder()
                .jobId(jobId)
                .subscriptionId(sub.getSubscriptionId())
                .partnerId(sub.getPartnerId())
                .mode(mode)
                .targetDeliveryId(targetDeliveryId)
                .fromTime(fromInstant)
                .toTime(toInstant)
                .fromPartition(request.getFromOffset() != null ? request.getFromOffset().getPartition() : null)
                .fromOffset(request.getFromOffset() != null ? request.getFromOffset().getOffset() : null)
                .toOffset(request.getToOffset() != null ? request.getToOffset().getOffset() : null)
                .eventTypesJson(jsonUtils.toJson(request.getEventTypes()))
                .includeDelivered(request.isIncludeDelivered())
                .reason(request.getReason())
                .status(JobStatus.RUNNING)
                .estimatedEvents(eventsToReplay.size())
                .estimatedDurationSeconds(estDuration)
                .deliveredCount(0)
                .failedCount(0)
                .skippedCount(0)
                .requestedBy(identity.actorId())
                .createdAt(now)
                .build());

        int delivered = 0;
        int failed = 0;
        int skipped = 0;
        List<String> subEventTypes = jsonUtils.toStringList(sub.getEventTypesJson());
        List<String> filterTypes = request.getEventTypes();

        for (DomainEventArchiveEntity archived : eventsToReplay) {
            if (!subEventTypes.contains(archived.getEventType())
                    || (filterTypes != null && !filterTypes.isEmpty() && !filterTypes.contains(archived.getEventType()))) {
                skipped++;
                continue;
            }
            Map<String, Object> env = jsonUtils.toMap(archived.getEnvelopeJson());
            String outcome = deliverToSubscription(sub, env, archived.getKafkaTopic(),
                    archived.getKafkaPartition(), archived.getKafkaOffset(), true, 1);
            if ("DELIVERED".equals(outcome)) delivered++;
            else if ("SKIPPED".equals(outcome) || "SUPPRESSED".equals(outcome)) skipped++;
            else failed++;
        }

        job.setDeliveredCount(delivered);
        job.setFailedCount(failed);
        job.setSkippedCount(skipped);
        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        replayJobRepository.save(job);

        outboxService.publishIntegrationEvent("integration.replay.completed", jobId, Map.of(
                "jobId", jobId,
                "subscriptionId", sub.getSubscriptionId(),
                "partnerId", sub.getPartnerId(),
                "mode", mode.name(),
                "delivered", delivered,
                "failed", failed,
                "skipped", skipped,
                "requestedBy", identity.actorId()
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", jobId);
        response.put("subscriptionId", sub.getSubscriptionId());
        response.put("mode", mode.name());
        response.put("status", job.getStatus().name());
        response.put("estimatedEvents", eventsToReplay.size());
        response.put("estimatedDurationSeconds", estDuration);
        response.put("counts", Map.of("delivered", delivered, "failed", failed, "skipped", skipped));
        response.put("createdAt", now.toString());

        idempotencyService.save("REPLAY:" + idOrRange, idempotencyKey, identity.actorId(),
                request, HttpStatus.ACCEPTED.value(), response, Duration.ofHours(24));
        return response;
    }

    /**
     * INT-03: Deliver Webhooks from Domain Events.
     */
    @Transactional
    public DispatchSummary processDomainEvent(Map<String, Object> envelope, String topic, int partition, long offset) {
        String eventId = Objects.toString(envelope.get("eventId"), "evt_" + CryptoUtils.randomHex(6));
        String eventType = Objects.toString(envelope.get("eventType"), Objects.toString(envelope.get("type"), ""));
        String subjectKey = Objects.toString(envelope.get("key"), Objects.toString(envelope.get("policyId"), ""));
        Instant occurredAt = envelope.get("occurredAt") != null
                ? Instant.parse(String.valueOf(envelope.get("occurredAt")))
                : Instant.now();

        updateTenantMapIfApplicable(eventType, envelope);

        // Archive compact event for replay support (INT-05)
        if (!archiveRepository.existsById(eventId)) {
            archiveRepository.save(DomainEventArchiveEntity.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .kafkaTopic(topic)
                    .kafkaPartition(partition)
                    .kafkaOffset(offset)
                    .subjectKey(subjectKey)
                    .envelopeJson(jsonUtils.toJson(envelope))
                    .occurredAt(occurredAt)
                    .archivedAt(Instant.now())
                    .build());
        }

        if (!allowList.isEventExportable(eventType)) {
            return new DispatchSummary("SKIP", "NOT_ALLOW_LISTED", 0, 0, 0);
        }

        List<WebhookSubscriptionEntity> activeSubs = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);
        int delivered = 0;
        int retried = 0;
        int dlq = 0;
        int matched = 0;

        for (WebhookSubscriptionEntity sub : activeSubs) {
            if (deliveryRepository.existsBySubscriptionIdAndEventId(sub.getSubscriptionId(), eventId)) {
                continue; // Deduplicated per eventId + subscriptionId
            }
            if (!matchesSubscription(sub, eventType, envelope, subjectKey)) {
                continue;
            }
            matched++;
            String outcome = deliverToSubscription(sub, envelope, topic, partition, offset, false, 1);
            switch (outcome) {
                case "DELIVERED" -> delivered++;
                case "RETRY" -> retried++;
                case "DLQ" -> dlq++;
                default -> {}
            }
        }

        if (matched == 0) {
            return new DispatchSummary("SKIP", "NO_SUBSCRIPTION", 0, 0, 0);
        }
        String overall = dlq > 0 ? "DLQ" : (retried > 0 ? "RETRY" : "DELIVERED");
        return new DispatchSummary(overall, overall, delivered, retried, dlq);
    }

    /**
     * Participates in Data Rights Requests (Section 2.15).
     */
    @Transactional
    public void handleDataRequestReceived(String requestId) {
        outboxService.publishToTopic(properties.getKafka().getIdentityTopic(), "data.request.part.completed", requestId, Map.of(
                "requestId", requestId,
                "service", "integration-hub-service",
                "outcome", "NO_PERSONAL_DATA_HELD",
                "retainedCategories", List.of("DELIVERY_IDS_AND_CODES", "INBOUND_RECEIPT_COUNTS", "INVOCATION_HASHES_7Y"),
                "note", "Hub holds no personal data; partners receiving webhooks remain responsible under DPAs."
        ));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSubscriptions() {
        HubIdentity identity = accessGuard.requireRoles(
                HubRole.PARTNER_ADMIN, HubRole.INTEGRATION_MANAGER, HubRole.ADMIN, HubRole.AUDITOR);
        return subscriptionRepository.findAll().stream()
                .filter(s -> s.getStatus() != SubscriptionStatus.DELETED)
                .filter(s -> !accessGuard.isPartnerScopedOnly(identity) || s.getPartnerId().equals(identity.partnerId()))
                .map(s -> toSubscriptionResponse(s, null))
                .toList();
    }

    private String deliverToSubscription(WebhookSubscriptionEntity sub, Map<String, Object> envelope,
                                         String topic, int partition, long offset, boolean isReplay, int attempt) {
        String eventId = Objects.toString(envelope.get("eventId"), "evt_" + CryptoUtils.randomHex(6));
        String eventType = Objects.toString(envelope.get("eventType"), Objects.toString(envelope.get("type"), ""));
        String subjectKey = Objects.toString(envelope.get("key"), Objects.toString(envelope.get("policyId"), ""));
        Instant now = Instant.now();
        String deliveryId = "dlv_" + CryptoUtils.randomHex(4);

        PartnerEntity partner = partnerRepository.findById(sub.getPartnerId()).orElse(null);
        if (partner == null || partner.getStatus() != PartnerStatus.ACTIVE) {
            return "SKIPPED";
        }
        List<String> allowedEvents = jsonUtils.toStringList(partner.getAllowedEventTypesJson());
        if (!allowedEvents.contains(eventType)) {
            return "SKIPPED";
        }

        // Project payload to partner's current data scope & replace internal ids with partner-scoped references
        DataScopeLevel effectiveScope = partner.getDataScope().ordinal() < sub.getDataScope().ordinal()
                ? partner.getDataScope() : sub.getDataScope();
        Map<String, Object> projectedData = projectWebhookPayload(partner.getPartnerId(), eventType, effectiveScope, envelope);

        // Run hub.pre-dispatch extension hook (TRANSFORM)
        Map<String, Object> hookOut = extensionService.invokeInternalHubHook(
                "hub.pre-dispatch", partner.getTenantId(), subjectKey, projectedData);
        if (Boolean.TRUE.equals(hookOut.get("suppress"))) {
            recordDelivery(deliveryId, sub, eventId, eventType, subjectKey, DeliveryStatus.SUPPRESSED,
                    attempt, null, 0L, 0, topic, partition, offset, "SUPPRESSED_BY_HOOK", null, isReplay);
            return "SUPPRESSED";
        }
        if (hookOut.get("redactFields") instanceof List<?> redactList) {
            for (Object f : redactList) {
                projectedData.remove(String.valueOf(f));
            }
        }
        if (hookOut.get("masked") instanceof Map<?, ?> masked) {
            for (Map.Entry<?, ?> entry : masked.entrySet()) {
                if (projectedData.containsKey(String.valueOf(entry.getKey()))) {
                    projectedData.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        Map<String, Object> bodyMap = new LinkedHashMap<>();
        if (sub.getFormat() == WebhookFormat.CLOUDEVENTS_JSON) {
            bodyMap.put("specversion", "1.0");
            bodyMap.put("id", eventId);
            bodyMap.put("type", "agrishield." + eventType);
            bodyMap.put("source", "urn:agrishield:hub");
            bodyMap.put("time", Objects.toString(envelope.get("occurredAt"), now.toString()));
            bodyMap.put("data", projectedData);
        } else {
            bodyMap.put("id", eventId);
            bodyMap.put("type", eventType);
            bodyMap.put("occurredAt", Objects.toString(envelope.get("occurredAt"), now.toString()));
            bodyMap.put("data", projectedData);
        }

        String rawBody = jsonUtils.toJson(bodyMap);
        byte[] bodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
        if (bodyBytes.length > properties.getWebhook().getMaxPayloadBytes()) {
            recordDelivery(deliveryId, sub, eventId, eventType, subjectKey, DeliveryStatus.DEAD_LETTERED,
                    attempt, null, 0L, bodyBytes.length, topic, partition, offset, "PAYLOAD_TOO_LARGE", null, isReplay);
            publishHookFailed(deliveryId, sub, eventId, "PAYLOAD_TOO_LARGE", attempt, true);
            notificationClient.notifyStaff("INTEGRATION_MANAGER", "Webhook payload too large",
                    "Subscription " + sub.getSubscriptionId() + " exceeded 256 KB");
            return "DLQ";
        }

        // Re-check egress SSRF before every delivery
        try {
            egressValidator.validatePublicHttpsUrl(sub.getTargetUrl(), "TARGET_BLOCKED",
                    "DNS now resolves to a disallowed address");
        } catch (HubException ex) {
            recordDelivery(deliveryId, sub, eventId, eventType, subjectKey, DeliveryStatus.RETRYING,
                    attempt, null, 0L, bodyBytes.length, topic, partition, offset, "TARGET_BLOCKED",
                    now.plusSeconds(RETRY_BACKOFF_SECONDS[0]), isReplay);
            return "RETRY";
        }

        // Sign with active key (and previous key if in overlap)
        String ts = String.valueOf(now.getEpochSecond());
        String sigHex = dataVaultClient.signPayload(sub.getKeyId(), ts, rawBody);
        String sigHeader = "t=" + ts + ",v1=" + sigHex;
        if (sub.getPreviousKeyId() != null && sub.getOverlapEndsAt() != null && sub.getOverlapEndsAt().isAfter(now)) {
            String prevSig = dataVaultClient.signPayload(sub.getPreviousKeyId(), ts, rawBody);
            sigHeader = sigHeader + ",v1=" + prevSig;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-AgriShield-Event-Id", eventId);
        headers.put("X-AgriShield-Delivery-Id", deliveryId);
        headers.put("X-AgriShield-Attempt", String.valueOf(attempt));
        headers.put("X-AgriShield-Replay", String.valueOf(isReplay));
        headers.put("X-AgriShield-Timestamp", ts);
        headers.put("X-AgriShield-Signature", sigHeader);

        WebhookHttpClient.HttpCallResult callResult = webhookHttpClient.post(
                sub.getTargetUrl(), headers, rawBody, properties.getWebhook().getDeliveryTimeoutMs());

        if (callResult.is2xx()) {
            recordDelivery(deliveryId, sub, eventId, eventType, subjectKey, DeliveryStatus.DELIVERED,
                    attempt, callResult.statusCode(), callResult.latencyMs(), bodyBytes.length,
                    topic, partition, offset, null, null, isReplay);
            outboxService.publishIntegrationEvent("integration.hook.dispatched", deliveryId, Map.of(
                    "deliveryId", deliveryId,
                    "subscriptionId", sub.getSubscriptionId(),
                    "partnerId", sub.getPartnerId(),
                    "eventId", eventId,
                    "eventType", eventType,
                    "status", "DELIVERED",
                    "responseCode", callResult.statusCode(),
                    "attempts", attempt,
                    "latencyMs", callResult.latencyMs()
            ));
            return "DELIVERED";
        }

        if (Integer.valueOf(410).equals(callResult.statusCode())) {
            sub.setStatus(SubscriptionStatus.PAUSED_AUTO);
            sub.setPauseReason(PauseReason.ENDPOINT_GONE);
            sub.setVersion(sub.getVersion() + 1);
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
            recordDelivery(deliveryId, sub, eventId, eventType, subjectKey, DeliveryStatus.PAUSED,
                    attempt, 410, callResult.latencyMs(), bodyBytes.length,
                    topic, partition, offset, "ENDPOINT_GONE", null, isReplay);
            outboxService.publishIntegrationEvent("integration.subscription.changed", sub.getSubscriptionId(), Map.of(
                    "subscriptionId", sub.getSubscriptionId(),
                    "partnerId", sub.getPartnerId(),
                    "change", "AUTO_PAUSED",
                    "reason", "ENDPOINT_GONE"
            ));
            notificationClient.notifyPartner(sub.getPartnerId(), "Webhook paused (410 Gone)",
                    "Subscription " + sub.getSubscriptionId() + " returned 410 Gone");
            return "PAUSED";
        }

        // Retry or DLQ
        if (attempt >= RETRY_BACKOFF_SECONDS.length) {
            recordDelivery(deliveryId, sub, eventId, eventType, subjectKey, DeliveryStatus.DEAD_LETTERED,
                    attempt, callResult.statusCode(), callResult.latencyMs(), bodyBytes.length,
                    topic, partition, offset, "RETRIES_EXHAUSTED", null, isReplay);
            publishHookFailed(deliveryId, sub, eventId, "RETRIES_EXHAUSTED", attempt, true);
            return "DLQ";
        }

        int backoff = callResult.retryAfterSeconds() != null
                ? callResult.retryAfterSeconds()
                : RETRY_BACKOFF_SECONDS[Math.min(attempt - 1, RETRY_BACKOFF_SECONDS.length - 1)];
        recordDelivery(deliveryId, sub, eventId, eventType, subjectKey, DeliveryStatus.RETRYING,
                attempt, callResult.statusCode(), callResult.latencyMs(), bodyBytes.length,
                topic, partition, offset, "RECEIVER_ERROR", now.plusSeconds(backoff), isReplay);
        checkAutoPauseBySuccessRate(sub, now);
        return "RETRY";
    }

    private void checkAutoPauseBySuccessRate(WebhookSubscriptionEntity sub, Instant now) {
        List<WebhookDeliveryEntity> recent = deliveryRepository.findBySubscriptionIdAndCreatedAtBetween(
                sub.getSubscriptionId(),
                now.minus(Duration.ofMinutes(properties.getWebhook().getAutoPauseWindowMinutes())),
                now);
        if (recent.size() < 5) return;
        long ok = recent.stream().filter(d -> d.getStatus() == DeliveryStatus.DELIVERED).count();
        double rate = (double) ok / recent.size();
        if (rate < properties.getWebhook().getAutoPauseSuccessThreshold()) {
            sub.setStatus(SubscriptionStatus.PAUSED_AUTO);
            sub.setPauseReason(PauseReason.LOW_SUCCESS_RATE);
            sub.setVersion(sub.getVersion() + 1);
            sub.setUpdatedAt(now);
            subscriptionRepository.save(sub);
            outboxService.publishIntegrationEvent("integration.subscription.changed", sub.getSubscriptionId(), Map.of(
                    "subscriptionId", sub.getSubscriptionId(),
                    "partnerId", sub.getPartnerId(),
                    "change", "AUTO_PAUSED",
                    "reason", "LOW_SUCCESS_RATE"
            ));
            notificationClient.notifyPartner(sub.getPartnerId(), "Webhook auto-paused",
                    "Success rate fell below 50% for subscription " + sub.getSubscriptionId());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> projectWebhookPayload(String partnerId, String eventType,
                                                      DataScopeLevel scope, Map<String, Object> envelope) {
        Map<String, Object> source = envelope.get("payload") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : envelope;
        Set<String> allowed = allowList.allowedFieldsFor(eventType, scope);
        Map<String, Object> projected = new LinkedHashMap<>();

        if (source.get("policyId") != null) {
            projected.put("policyRef", dataVaultClient.partnerScopedHash(partnerId, String.valueOf(source.get("policyId"))));
        }
        if (source.get("claimId") != null) {
            projected.put("claimRef", dataVaultClient.partnerScopedHash(partnerId, String.valueOf(source.get("claimId"))));
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (PlatformAllowList.PII_FIELD_NAMES.contains(key)) {
                continue;
            }
            if (Set.of("policyId", "claimId", "farmerId", "eventId", "eventType").contains(key)) {
                continue;
            }
            if (key.startsWith("x_") || allowed.contains("*") || allowed.contains(key)) {
                projected.put(key, entry.getValue());
            }
        }
        return projected;
    }

    @SuppressWarnings("unchecked")
    private boolean matchesSubscription(WebhookSubscriptionEntity sub, String eventType,
                                        Map<String, Object> envelope, String subjectKey) {
        List<String> subscribedTypes = jsonUtils.toStringList(sub.getEventTypesJson());
        if (!subscribedTypes.contains(eventType)) {
            return false;
        }
        Map<String, Object> filters = jsonUtils.toMap(sub.getFiltersJson());
        if (filters.isEmpty()) return true;

        Map<String, Object> payload = envelope.get("payload") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : envelope;

        if (filters.get("regionCodes") instanceof List<?> regs && !regs.isEmpty()) {
            String reg = Objects.toString(payload.get("regionCode"), "");
            if (!regs.stream().map(String::valueOf).toList().contains(reg)) return false;
        }
        if (filters.get("productIds") instanceof List<?> prods && !prods.isEmpty()) {
            String prod = Objects.toString(payload.get("productId"), "");
            if (!prods.stream().map(String::valueOf).toList().contains(prod)) return false;
        }
        if (filters.get("cropCodes") instanceof List<?> crops && !crops.isEmpty()) {
            String crop = Objects.toString(payload.get("cropCode"), "");
            if (!crops.stream().map(String::valueOf).toList().contains(crop)) return false;
        }
        if (filters.get("tenantIds") instanceof List<?> tenants && !tenants.isEmpty()) {
            String tenant = Objects.toString(payload.get("tenantId"),
                    Objects.toString(payload.get("sponsorOrgId"), ""));
            if (tenant.isBlank() && !subjectKey.isBlank()) {
                tenant = tenantMapRepository.findById(subjectKey).map(TenantMapEntryEntity::getTenantId).orElse("");
            }
            if (!tenants.stream().map(String::valueOf).toList().contains(tenant)) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void updateTenantMapIfApplicable(String eventType, Map<String, Object> envelope) {
        Map<String, Object> payload = envelope.get("payload") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : envelope;
        String subject = Objects.toString(payload.get("policyId"), Objects.toString(envelope.get("key"), ""));
        String tenant = Objects.toString(payload.get("tenantId"),
                Objects.toString(payload.get("sponsorOrgId"), Objects.toString(payload.get("payerRef"), "")));
        String groupId = payload.get("groupId") != null ? String.valueOf(payload.get("groupId")) : null;
        if (!subject.isBlank() && !tenant.isBlank()
                && Set.of("policy.issued", "farmer.group.member.confirmed", "premium.paid").contains(eventType)) {
            tenantMapRepository.save(TenantMapEntryEntity.builder()
                    .subjectRef(subject)
                    .tenantId(tenant)
                    .groupId(groupId)
                    .source(eventType)
                    .updatedAt(Instant.now())
                    .build());
        }
    }

    private void recordDelivery(String deliveryId, WebhookSubscriptionEntity sub, String eventId, String eventType,
                                String subjectKey, DeliveryStatus status, int attempts, Integer responseCode,
                                Long latencyMs, int payloadBytes, String topic, int partition, long offset,
                                String reasonCode, Instant nextRetryAt, boolean replay) {
        Instant now = Instant.now();
        deliveryRepository.save(WebhookDeliveryEntity.builder()
                .deliveryId(deliveryId)
                .subscriptionId(sub.getSubscriptionId())
                .partnerId(sub.getPartnerId())
                .eventId(eventId)
                .eventType(eventType)
                .subjectKey(subjectKey)
                .status(status)
                .attempts(attempts)
                .responseCode(responseCode)
                .latencyMs(latencyMs)
                .payloadBytes(payloadBytes)
                .kafkaTopic(topic)
                .kafkaPartition(partition)
                .kafkaOffset(offset)
                .reasonCode(reasonCode)
                .nextRetryAt(nextRetryAt)
                .replay(replay)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private void publishHookFailed(String deliveryId, WebhookSubscriptionEntity sub, String eventId,
                                   String reasonCode, int attempts, boolean finalFailure) {
        outboxService.publishIntegrationEvent("integration.hook.failed", deliveryId, Map.of(
                "deliveryId", deliveryId,
                "subscriptionId", sub.getSubscriptionId(),
                "partnerId", sub.getPartnerId(),
                "eventId", eventId,
                "reasonCode", reasonCode,
                "attempts", attempts,
                "finalFailure", finalFailure
        ));
    }

    private ChallengeOutcome sendOwnershipChallenge(String targetUrl, String keyId) {
        String nonce = CryptoUtils.randomHex(12);
        String body = jsonUtils.toJson(Map.of("type", "subscription.challenge", "challenge", nonce));
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = dataVaultClient.signPayload(keyId, ts, body);

        WebhookHttpClient.HttpCallResult result = webhookHttpClient.post(
                targetUrl,
                Map.of(
                        "Content-Type", "application/json",
                        "X-AgriShield-Timestamp", ts,
                        "X-AgriShield-Signature", "t=" + ts + ",v1=" + sig
                ),
                body,
                properties.getWebhook().getChallengeTimeoutMs());
        if (result.is2xx()) {
            return new ChallengeOutcome(true, null);
        }
        return new ChallengeOutcome(false, "Challenge failed with " + result.outcome()
                + (result.statusCode() != null ? " (HTTP " + result.statusCode() + ")" : ""));
    }

    private void validateFiltersWithinPartnerScope(PartnerEntity partner, RegisterSubscriptionRequest.SubscriptionFilters filters) {
        if (filters == null) return;
        Map<String, Object> vis = jsonUtils.toMap(partner.getVisibilityJson());
        checkFilterSubset(filters.getRegionCodes(), vis.get("regionCodes"));
        checkFilterSubset(filters.getProductIds(), vis.get("productIds"));
        checkFilterSubset(filters.getTenantIds(), vis.get("tenantIds"));
    }

    private void checkFilterSubset(List<String> requested, Object allowedObj) {
        if (requested == null || requested.isEmpty()) return;
        if (!(allowedObj instanceof List<?> allowed) || allowed.isEmpty()) return;
        Set<String> set = new HashSet<>();
        allowed.forEach(x -> set.add(String.valueOf(x)));
        for (String r : requested) {
            if (!set.contains(r)) {
                throw HubException.unprocessable("FILTER_OUT_OF_SCOPE", "A filter is outside your approved scope");
            }
        }
    }

    private WebhookSubscriptionEntity loadAccessibleSubscription(String id, HubIdentity identity) {
        WebhookSubscriptionEntity sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> HubException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found"));
        accessGuard.assertCanAccessPartnerResource(identity, sub.getPartnerId(),
                "SUBSCRIPTION_NOT_FOUND", "Subscription not found");
        return sub;
    }

    private Map<String, Object> computeHealth(WebhookSubscriptionEntity sub, List<WebhookDeliveryEntity> deliveries) {
        long total = deliveries.size();
        long ok = deliveries.stream().filter(d -> d.getStatus() == DeliveryStatus.DELIVERED).count();
        long retries = deliveries.stream().mapToLong(d -> Math.max(0, d.getAttempts() - 1)).sum();
        long dlq = deliveryRepository.countBySubscriptionIdAndStatus(sub.getSubscriptionId(), DeliveryStatus.DEAD_LETTERED);
        long backlog = deliveryRepository.countBySubscriptionIdAndStatus(sub.getSubscriptionId(), DeliveryStatus.RETRYING);

        List<Long> latencies = deliveries.stream()
                .map(WebhookDeliveryEntity::getLatencyMs)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        Instant lastSuccess = deliveries.stream()
                .filter(d -> d.getStatus() == DeliveryStatus.DELIVERED)
                .map(WebhookDeliveryEntity::getCreatedAt)
                .max(Comparator.naturalOrder()).orElse(null);
        Instant lastFailure = deliveries.stream()
                .filter(d -> d.getStatus() != DeliveryStatus.DELIVERED)
                .map(WebhookDeliveryEntity::getCreatedAt)
                .max(Comparator.naturalOrder()).orElse(null);

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("successRate", total == 0 ? 1.0 : Math.round(((double) ok / total) * 1000.0) / 1000.0);
        health.put("p50Ms", percentile(latencies, 0.50));
        health.put("p95Ms", percentile(latencies, 0.95));
        health.put("p99Ms", percentile(latencies, 0.99));
        health.put("retries", retries);
        health.put("deadLettered", dlq);
        health.put("backlog", backlog);
        if (lastSuccess != null) health.put("lastSuccessAt", lastSuccess.toString());
        if (lastFailure != null) health.put("lastFailureAt", lastFailure.toString());
        return health;
    }

    public static long percentile(List<Long> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) return 0L;
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.min(Math.max(idx, 0), sorted.size() - 1));
    }

    private Map<String, Object> toSubscriptionResponse(WebhookSubscriptionEntity sub, String newSecret) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subscriptionId", sub.getSubscriptionId());
        m.put("name", sub.getName());
        m.put("environment", sub.getEnvironment().name());
        m.put("status", sub.getStatus().name());
        if (sub.getPauseReason() != null) m.put("pauseReason", sub.getPauseReason().name());
        m.put("eventTypes", jsonUtils.toStringList(sub.getEventTypesJson()));
        if (sub.getFiltersJson() != null && !"null".equals(sub.getFiltersJson())) {
            m.put("filters", jsonUtils.toMap(sub.getFiltersJson()));
        }
        m.put("rateLimitPerSecond", sub.getRateLimitPerSecond());
        m.put("version", sub.getVersion());
        if (newSecret != null) {
            m.put("key", Map.of(
                    "keyId", sub.getKeyId(),
                    "previousKeyId", Objects.toString(sub.getPreviousKeyId(), ""),
                    "overlapEndsAt", sub.getOverlapEndsAt().toString(),
                    "secret", newSecret
            ));
        }
        return m;
    }

    private record ChallengeOutcome(boolean passed, String failureReason) {}
    public record SubscriptionView(Map<String, Object> body, int version) {}
    public record DispatchSummary(String outcome, String code, int delivered, int retried, int dlq) {}
}
