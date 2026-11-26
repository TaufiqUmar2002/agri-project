package com.umar.auth.service;

import com.umar.auth.client.DataVaultClient;
import com.umar.auth.client.NotificationClient;
import com.umar.auth.config.AuthProperties;
import com.umar.auth.domain.entity.DataRequestEntity;
import com.umar.auth.domain.entity.UserAccountEntity;
import com.umar.auth.exception.AuthApiException;
import com.umar.auth.repository.DataRequestRepository;
import com.umar.auth.repository.RefreshSessionRepository;
import com.umar.auth.repository.UserAccountRepository;
import com.umar.auth.security.AuthIdentity;
import com.umar.auth.security.AuthSecurityContext;
import com.umar.auth.service.support.OtpChallengeService;
import com.umar.auth.service.support.OutboxService;
import com.umar.auth.service.support.RateLimitService;
import com.umar.enums.auth.*;
import com.umar.payload.request.auth.DataRightsRequest;
import com.umar.payload.response.auth.DataRightsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataRightsService {

    private static final List<DataRequestStatus> OPEN_STATUSES = List.of(
            DataRequestStatus.REQUESTED, DataRequestStatus.IN_PROGRESS, DataRequestStatus.ON_HOLD);

    private final DataRequestRepository dataRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final OtpChallengeService otpChallengeService;
    private final DataVaultClient dataVaultClient;
    private final NotificationClient notificationClient;
    private final OutboxService outboxService;
    private final RateLimitService rateLimitService;
    private final IdentityContextService identityContextService;
    private final AuthProperties properties;

    @Transactional
    public DataRightsResponse submitDataRightsRequest(
            String delegationHeader,
            String stepUpToken,
            String deviceId,
            DataRightsRequest request) {
        AuthIdentity actor = AuthSecurityContext.require();
        if (delegationHeader != null && !delegationHeader.isBlank()) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only the account holder can make this request");
        }
        if (actor.userId() == null) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Missing or expired token");
        }
        UUID userId = actor.userId();

        otpChallengeService.consumeStepUpToken(stepUpToken, userId, StepUpActionClass.DATA_RIGHTS);

        if (request.getType() == DataRequestType.ERASURE && !Boolean.TRUE.equals(request.getConfirmErasure())) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "confirmErasure must be true for ERASURE");
        }
        if (request.getType() == DataRequestType.EXPORT
                && (request.getDeliveryChannel() == null || (!"SMS".equalsIgnoreCase(request.getDeliveryChannel())
                        && !"WHATSAPP".equalsIgnoreCase(request.getDeliveryChannel())))) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "deliveryChannel (SMS or WHATSAPP) is required for EXPORT");
        }

        rateLimitService.checkAndIncrement("dr:" + userId, properties.getLimits().getDataRightsPerUserPerDay(), 86400);

        Optional<DataRequestEntity> open = dataRequestRepository.findFirstByUserIdAndTypeAndStatusIn(
                userId, request.getType(), OPEN_STATUSES);
        if (open.isPresent()) {
            throw new AuthApiException(HttpStatus.CONFLICT, "DATA_REQUEST_IN_PROGRESS",
                    "A request is already in progress", Map.of("requestId", open.get().getRequestId().toString()));
        }

        List<DataCategory> categories = (request.getCategories() == null || request.getCategories().isEmpty())
                ? List.of(DataCategory.values())
                : request.getCategories();

        Instant now = Instant.now();
        Instant coolingOffUntil = null;
        if (request.getType() == DataRequestType.ERASURE && deviceId != null && !deviceId.isBlank()
                && !refreshSessionRepository.existsByUserIdAndDeviceId(userId, deviceId)) {
            coolingOffUntil = now.plus(48, ChronoUnit.HOURS);
            userAccountRepository.findById(userId).ifPresent(u ->
                    notificationClient.sendSecurityAlert(u.getPhoneToken(), "ERASURE_COOLING_OFF", u.getPreferredLanguage()));
        }

        Instant dueBy = request.getType() == DataRequestType.EXPORT
                ? now.plus(7, ChronoUnit.DAYS)
                : now.plus(30, ChronoUnit.DAYS);

        DataRequestEntity saved = dataRequestRepository.save(DataRequestEntity.builder()
                .requestId(UUID.randomUUID())
                .userId(userId)
                .type(request.getType())
                .categoriesCsv(categories.stream().map(Enum::name).collect(Collectors.joining(",")))
                .deliveryChannel(request.getDeliveryChannel())
                .reason(request.getReason())
                .status(DataRequestStatus.REQUESTED)
                .coolingOffUntil(coolingOffUntil)
                .dueBy(dueBy)
                .requestedAt(now)
                .build());

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "data.request.received");
        evt.put("requestId", saved.getRequestId().toString());
        evt.put("userId", userId.toString());
        evt.put("type", saved.getType().name());
        evt.put("categories", categories.stream().map(Enum::name).toList());
        evt.put("dueBy", dueBy.toString());
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getIdentityTopic(), "data.request.received",
                saved.getRequestId().toString(), evt);

        String notice = request.getType() == DataRequestType.EXPORT
                ? "We will send you a secure link by " + request.getDeliveryChannel() + ". It stays valid for 7 days."
                : "Your erasure request has been accepted. Personal identifiers will be shredded; legally required policy and payout records will be retained in restricted form.";

        return DataRightsResponse.builder()
                .requestId(saved.getRequestId().toString())
                .type(saved.getType().name())
                .status(saved.getStatus().name())
                .requestedAt(now.toString())
                .dueBy(dueBy.toString())
                .coolingOffUntil(coolingOffUntil != null ? coolingOffUntil.toString() : null)
                .notice(notice)
                .build();
    }

    @Transactional
    public void handlePartCompleted(UUID requestId, String serviceName, List<String> retainedCategories) {
        DataRequestEntity req = dataRequestRepository.findById(requestId).orElse(null);
        if (req == null) return;

        Set<String> parts = new LinkedHashSet<>();
        if (req.getCompletedPartsCsv() != null && !req.getCompletedPartsCsv().isBlank()) {
            parts.addAll(Arrays.asList(req.getCompletedPartsCsv().split(",")));
        }
        parts.add(serviceName);
        req.setCompletedPartsCsv(String.join(",", parts));

        if (retainedCategories != null && !retainedCategories.isEmpty()) {
            Set<String> retained = new LinkedHashSet<>();
            if (req.getRetainedCategoriesCsv() != null && !req.getRetainedCategoriesCsv().isBlank()) {
                retained.addAll(Arrays.asList(req.getRetainedCategoriesCsv().split(",")));
            }
            retained.addAll(retainedCategories);
            req.setRetainedCategoriesCsv(String.join(",", retained));
        }

        if (req.getType() == DataRequestType.ERASURE) {
            List<String> holds = dataVaultClient.listActiveLegalHolds(req.getUserId());
            if (!holds.isEmpty()) {
                req.setStatus(DataRequestStatus.ON_HOLD);
                dataRequestRepository.save(req);
                return;
            }
            dataVaultClient.shredSubject(req.getUserId(), "USER_ERASURE_REQUEST");
            userAccountRepository.findById(req.getUserId()).ifPresent(u -> {
                u.setStatus(AccountStatus.CLOSED);
                userAccountRepository.save(u);
                identityContextService.evict(u.getUserId());
            });
        }

        boolean partial = req.getRetainedCategoriesCsv() != null && !req.getRetainedCategoriesCsv().isBlank();
        req.setStatus(partial ? DataRequestStatus.PARTIAL : DataRequestStatus.COMPLETED);
        req.setCompletedAt(Instant.now());
        dataRequestRepository.save(req);

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "data.request.completed");
        evt.put("requestId", req.getRequestId().toString());
        evt.put("userId", req.getUserId().toString());
        evt.put("type", req.getType().name());
        evt.put("outcome", req.getStatus().name());
        evt.put("retainedCategories", req.getRetainedCategoriesCsv() == null ? List.of() :
                Arrays.asList(req.getRetainedCategoriesCsv().split(",")));
        evt.put("timestamp", req.getCompletedAt().toString());
        outboxService.enqueue(properties.getKafka().getIdentityTopic(), "data.request.completed",
                req.getRequestId().toString(), evt);
    }
}
