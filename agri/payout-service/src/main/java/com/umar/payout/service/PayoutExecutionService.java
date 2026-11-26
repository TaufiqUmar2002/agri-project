package com.umar.payout.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.payout.FallbackAction;
import com.umar.enums.payout.PayoutStatus;
import com.umar.enums.payout.ProviderStatus;
import com.umar.payload.request.payout.FallbackPayoutRequest;
import com.umar.payload.response.payout.PayoutDetailResponse;
import com.umar.payload.response.payout.PayoutListResponse;
import com.umar.payout.client.PayoutExternalClients;
import com.umar.payout.config.PayoutProperties;
import com.umar.payout.domain.*;
import com.umar.payout.exception.PayoutApiException;
import com.umar.payout.repository.*;
import com.umar.payout.security.PayoutIdentity;
import com.umar.payout.security.PayoutIdentityFilter;
import com.umar.payout.service.support.PayoutOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayoutExecutionService {

    private final PayoutInstructionRepository payoutRepository;
    private final ProviderConnectionRepository connectionRepository;
    private final CashOutAgentRepository agentRepository;
    private final PayoutCallbackInboxRepository callbackInboxRepository;
    private final PayoutInboxEventRepository inboxRepository;
    private final PayoutExternalClients clients;
    private final PayoutOutboxService outboxService;
    private final PayoutProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public PayoutInstructionEntity onClaimReadyForPayout(String eventId, String eventType, String claimId,
                                                         int attempt, String farmerId, String route, String destinationId) {
        if (eventId != null && inboxRepository.existsById(eventId)) {
            return payoutRepository.findByIdempotencyKey(claimId + ":" + attempt).orElse(null);
        }
        if (eventId != null) {
            inboxRepository.save(PayoutInboxEventEntity.builder()
                    .eventId(eventId).eventType(eventType).processedAt(Instant.now()).build());
        }
        String idemKey = claimId + ":" + attempt;
        Optional<PayoutInstructionEntity> existing = payoutRepository.findByIdempotencyKey(idemKey);
        if (existing.isPresent()) return existing.get();

        if (!clients.isClaimsServiceAvailable()) {
            throw new PayoutApiException(HttpStatus.SERVICE_UNAVAILABLE, "CLAIMS_UNAVAILABLE", "Claims instruction unavailable");
        }
        PayoutExternalClients.ClaimInstructionView inst = clients.fetchPaymentInstruction(claimId);
        if (!inst.payable()) {
            return null;
        }

        String effRoute = (route != null && !route.isBlank()) ? route : inst.route();
        Instant now = Instant.now();
        String payoutId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String origRef = clients.sha256(idemKey).substring(0, 16);

        PayoutExternalClients.DestinationView dest = clients.resolveDestination(farmerId, effRoute);
        if (!dest.verified()) {
            PayoutInstructionEntity failed = payoutRepository.save(PayoutInstructionEntity.builder()
                    .payoutId(payoutId).claimId(claimId).attempt(attempt).idempotencyKey(idemKey).originatorRef(origRef)
                    .status(PayoutStatus.FAILED).route(effRoute).amountMinor(inst.amountMinor()).currency(inst.currency())
                    .reasonCode("DESTINATION_HOLD").retryable(false).holdReason(Objects.toString(dest.holdReason(), "WALLET_UNVERIFIED"))
                    .nextStep("FARMER_ACTION").createdAt(now).completedAt(now).hoursToResult(0.0).versionNum(1).build());
            outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.failed", claimId, Map.of(
                    "eventType", "payout.failed", "payoutId", payoutId, "claimId", claimId,
                    "reasonCode", "DESTINATION_HOLD", "nextStep", "FARMER_ACTION", "timestamp", now.toString()));
            return failed;
        }

        if ("CASH_OUT_AGENT".equals(effRoute)) {
            CashOutAgentEntity agent = agentRepository.findFirstByAreaCodeAndActiveTrue("DEFAULT")
                    .orElseGet(() -> agentRepository.save(CashOutAgentEntity.builder()
                            .agentId("agt_cash_1").areaCode("DEFAULT").name("Karatina Agent").active(true).maxSingleMinor(500_000L).build()));
            String code = String.valueOf(100000 + new Random().nextInt(900000));
            String voucherHash = clients.sha256(payoutId + ":" + code);
            PayoutInstructionEntity cashPay = payoutRepository.save(PayoutInstructionEntity.builder()
                    .payoutId(payoutId).claimId(claimId).attempt(attempt).idempotencyKey(idemKey).originatorRef(origRef)
                    .status(PayoutStatus.PENDING).route("CASH_OUT_AGENT").connection("CASHOUT")
                    .amountMinor(inst.amountMinor()).currency(inst.currency())
                    .destinationMask("CASH-OUT AGENT").cashOutAgentId(agent.getAgentId()).cashOutArea(agent.getAreaCode())
                    .voucherHash(voucherHash).voucherStatus("ISSUED")
                    .voucherExpiresAt(now.plus(properties.getRules().getVoucherExpiryDays(), ChronoUnit.DAYS))
                    .providerRef("CSH_" + payoutId).providerRefMasked("CS***" + payoutId.substring(payoutId.length() - 4))
                    .attemptsJson("[]").nextStep("FARMER_COLLECT").createdAt(now).initiatedAt(now).versionNum(1).build());
            outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.initiated", claimId, Map.of(
                    "eventType", "payout.initiated", "payoutId", payoutId, "claimId", claimId,
                    "route", "CASH_OUT_AGENT", "timestamp", now.toString()));
            return cashPay;
        }

        ProviderConnectionEntity conn = connectionRepository
                .findFirstByNetworkAndStatusOrderByPriorityOrderAsc(dest.network(), ProviderStatus.ENABLED)
                .orElse(null);
        if (conn == null) {
            return payoutRepository.save(PayoutInstructionEntity.builder()
                    .payoutId(payoutId).claimId(claimId).attempt(attempt).idempotencyKey(idemKey).originatorRef(origRef)
                    .status(PayoutStatus.WAITING_PROVIDER).route(effRoute).amountMinor(inst.amountMinor()).currency(inst.currency())
                    .destinationToken(dest.token()).destinationMask(dest.mask()).attemptsJson("[]")
                    .createdAt(now).versionNum(1).build());
        }

        return dispatchInstruction(payoutId, claimId, attempt, idemKey, origRef, effRoute, conn.getConnectionId(),
                inst.amountMinor(), inst.currency(), dest.token(), dest.mask(), now);
    }

    private PayoutInstructionEntity dispatchInstruction(String payoutId, String claimId, int attempt, String idemKey,
                                                        String origRef, String route, String connectionId,
                                                        long amountMinor, String currency, String destToken, String destMask,
                                                        Instant now) {
        PayoutExternalClients.ProviderDispatchResult res = clients.dispatchToProvider(connectionId, origRef, amountMinor, destToken);
        List<Map<String, Object>> attempts = List.of(Map.of(
                "connection", connectionId, "at", now.toString(),
                "accepted", res.accepted(), "status", res.status()));

        PayoutStatus status = res.accepted() ? PayoutStatus.PENDING : PayoutStatus.FAILED;
        String provRef = res.providerRef();
        PayoutInstructionEntity saved = payoutRepository.save(PayoutInstructionEntity.builder()
                .payoutId(payoutId).claimId(claimId).attempt(attempt).idempotencyKey(idemKey).originatorRef(origRef)
                .status(status).route(route).connection(connectionId)
                .amountMinor(amountMinor).currency(currency)
                .destinationToken(destToken).destinationMask(destMask)
                .providerRef(provRef).providerRefMasked(clients.maskProviderRef(provRef))
                .attemptsJson(writeJson(attempts))
                .reasonCode(res.reasonCode()).retryable(res.retryable())
                .nextCheckAt(status == PayoutStatus.PENDING ? now.plus(5, ChronoUnit.MINUTES) : null)
                .createdAt(now).initiatedAt(now)
                .completedAt(status == PayoutStatus.FAILED ? now : null)
                .versionNum(1).build());

        if (status == PayoutStatus.PENDING) {
            outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.initiated", claimId, Map.of(
                    "eventType", "payout.initiated", "payoutId", payoutId, "claimId", claimId,
                    "connection", connectionId, "timestamp", now.toString()));
        } else {
            outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.failed", claimId, Map.of(
                    "eventType", "payout.failed", "payoutId", payoutId, "claimId", claimId,
                    "reasonCode", Objects.toString(res.reasonCode(), "DISPATCH_FAILED"),
                    "nextStep", "SUPPORT_RETRY", "timestamp", now.toString()));
        }
        return saved;
    }

    @Transactional
    public void onCancelRequested(String eventId, String claimId) {
        if (eventId != null && inboxRepository.existsById(eventId)) return;
        if (eventId != null) {
            inboxRepository.save(PayoutInboxEventEntity.builder()
                    .eventId(eventId).eventType("claim.payout.cancel.requested").processedAt(Instant.now()).build());
        }
        Instant now = Instant.now();
        for (PayoutInstructionEntity p : payoutRepository.findByClaimId(claimId)) {
            p.setCancelRequested(true);
            if (p.getStatus() == PayoutStatus.CREATED || p.getStatus() == PayoutStatus.READY
                    || p.getStatus() == PayoutStatus.WAITING_INSTRUCTION || p.getStatus() == PayoutStatus.WAITING_PROVIDER
                    || ("CASH_OUT_AGENT".equals(p.getRoute()) && "ISSUED".equals(p.getVoucherStatus()))) {
                p.setStatus(PayoutStatus.CANCELLED);
                if ("ISSUED".equals(p.getVoucherStatus())) p.setVoucherStatus("VOIDED");
                p.setReasonCode("PAYOUT_CANCELLED");
                p.setCompletedAt(now);
                p.setVersionNum(p.getVersionNum() + 1);
                outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.failed", claimId, Map.of(
                        "eventType", "payout.failed", "payoutId", p.getPayoutId(), "claimId", claimId,
                        "reasonCode", "PAYOUT_CANCELLED", "timestamp", now.toString()));
            }
            payoutRepository.save(p);
        }
    }

    public PayoutDetailResponse getPayout(String payoutId) {
        PayoutIdentity actor = requireIdentity();
        if (!actor.isServiceCaller() && !actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.FINANCE,
                AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR, AuthRole.ADMIN)) {
            throw new PayoutApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        PayoutInstructionEntity p = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new PayoutApiException(HttpStatus.NOT_FOUND, "PAYOUT_NOT_FOUND", "Payout not found"));

        boolean canSeeFullRef = actor.hasAnyRole(AuthRole.FINANCE, AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR, AuthRole.ADMIN);
        Map<String, Object> cashOut = "CASH_OUT_AGENT".equals(p.getRoute())
                ? Map.of("agentId", Objects.toString(p.getCashOutAgentId(), ""),
                        "area", Objects.toString(p.getCashOutArea(), ""),
                        "voucherStatus", Objects.toString(p.getVoucherStatus(), ""),
                        "expiresAt", p.getVoucherExpiresAt() != null ? p.getVoucherExpiresAt().toString() : "")
                : null;
        Map<String, Object> stmtMatch = canSeeFullRef && p.getStatementMatchStatus() != null
                ? Map.of("status", p.getStatementMatchStatus(),
                        "statementDate", p.getStatementDate() != null ? p.getStatementDate().toString() : "")
                : null;

        return PayoutDetailResponse.builder()
                .payoutId(p.getPayoutId()).claimId(p.getClaimId()).attempt(p.getAttempt())
                .idempotencyKey(p.getIdempotencyKey()).status(p.getStatus().name())
                .escalated(p.isEscalated()).cancelRequested(p.isCancelRequested())
                .route(p.getRoute()).connection(p.getConnection())
                .amountMinor(p.getAmountMinor()).currency(p.getCurrency())
                .destinationMask(p.getDestinationMask())
                .providerRef(canSeeFullRef ? p.getProviderRef() : null)
                .providerRefMasked(p.getProviderRefMasked())
                .attempts(readList(p.getAttemptsJson()))
                .reasonCode(p.getReasonCode()).retryable(p.getRetryable()).holdReason(p.getHoldReason())
                .cashOut(cashOut).statementMatch(stmtMatch)
                .nextStep(p.getNextStep())
                .nextCheckAt(p.getNextCheckAt() != null ? p.getNextCheckAt().toString() : null)
                .createdAt(p.getCreatedAt().toString())
                .initiatedAt(p.getInitiatedAt() != null ? p.getInitiatedAt().toString() : null)
                .completedAt(p.getCompletedAt() != null ? p.getCompletedAt().toString() : null)
                .hoursToResult(p.getHoursToResult())
                .version(p.getVersionNum())
                .build();
    }

    @Transactional
    public Map<String, Object> handleProviderCallback(String provider, String rawBody, String signature, String timestampHdr, String nonceHdr) {
        if (timestampHdr == null || timestampHdr.isBlank()) {
            throw new PayoutApiException(HttpStatus.UNAUTHORIZED, "CALLBACK_EXPIRED", "Timestamp missing");
        }
        try {
            Instant ts = Instant.parse(timestampHdr);
            if (Math.abs(ChronoUnit.SECONDS.between(ts, Instant.now())) > properties.getRules().getCallbackSkewSeconds()) {
                throw new PayoutApiException(HttpStatus.UNAUTHORIZED, "CALLBACK_EXPIRED", "Callback timestamp expired");
            }
        } catch (PayoutApiException e) {
            throw e;
        } catch (Exception e) {
            throw new PayoutApiException(HttpStatus.UNAUTHORIZED, "CALLBACK_EXPIRED", "Invalid timestamp");
        }
        if (!clients.verifySignature(provider, rawBody, signature)) {
            throw new PayoutApiException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Not authorised");
        }

        JsonNode body;
        try {
            body = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new PayoutApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid callback JSON");
        }
        String provRef = body.path("providerRef").asString("");
        String originatorRef = body.path("originatorRef").asString("");
        String statusStr = body.path("status").asString("COMPLETED");
        String nonce = (nonceHdr != null && !nonceHdr.isBlank()) ? nonceHdr : provRef + ":" + statusStr;
        String bodyHash = clients.sha256(rawBody);

        Optional<PayoutCallbackInboxEntity> prevCb = callbackInboxRepository.findByProviderAndNonce(provider.toLowerCase(), nonce);
        if (prevCb.isPresent()) {
            if (!prevCb.get().getBodyHash().equals(bodyHash)) {
                throw new PayoutApiException(HttpStatus.CONFLICT, "CALLBACK_REPLAY", "Nonce reused with different payload");
            }
            return Map.of("accepted", true, "duplicate", true);
        }

        Instant now = Instant.now();
        callbackInboxRepository.save(PayoutCallbackInboxEntity.builder()
                .inboxId(UUID.randomUUID()).provider(provider.toLowerCase())
                .nonce(nonce).providerRef(provRef).bodyHash(bodyHash)
                .receivedAt(now).status("RECEIVED").build());

        PayoutInstructionEntity p = (!provRef.isBlank()
                ? payoutRepository.findByProviderRef(provRef)
                : Optional.<PayoutInstructionEntity>empty())
                .or(() -> !originatorRef.isBlank() ? payoutRepository.findByOriginatorRef(originatorRef) : Optional.empty())
                .orElse(null);

        if (p == null || p.getStatus() == PayoutStatus.COMPLETED || p.getStatus() == PayoutStatus.CANCELLED) {
            return Map.of("accepted", true);
        }

        if ("COMPLETED".equalsIgnoreCase(statusStr)) {
            p.setStatus(PayoutStatus.COMPLETED);
            if ("CASH_OUT_AGENT".equals(p.getRoute())) p.setVoucherStatus("COLLECTED");
            p.setCompletedAt(now);
            p.setHoursToResult(java.time.Duration.between(p.getCreatedAt(), now).toSeconds() / 3600.0);
            p.setVersionNum(p.getVersionNum() + 1);
            payoutRepository.save(p);
            outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.completed", p.getClaimId(), Map.of(
                    "eventType", "payout.completed", "payoutId", p.getPayoutId(), "claimId", p.getClaimId(),
                    "route", p.getRoute(), "amountMinor", p.getAmountMinor(), "currency", p.getCurrency(),
                    "providerRef", Objects.toString(p.getProviderRefMasked(), ""),
                    "completedAt", now.toString()));
        } else {
            p.setStatus(PayoutStatus.FAILED);
            p.setReasonCode(body.path("reasonCode").asString("PROVIDER_FAILED"));
            p.setRetryable(body.path("retryable").asBoolean(true));
            p.setCompletedAt(now);
            p.setVersionNum(p.getVersionNum() + 1);
            payoutRepository.save(p);
            outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.failed", p.getClaimId(), Map.of(
                    "eventType", "payout.failed", "payoutId", p.getPayoutId(), "claimId", p.getClaimId(),
                    "reasonCode", p.getReasonCode(), "nextStep", "SUPPORT_RETRY", "timestamp", now.toString()));
        }
        return Map.of("accepted", true, "status", p.getStatus().name());
    }

    @Transactional
    public PayoutDetailResponse fallbackPayout(String payoutId, FallbackPayoutRequest req) {
        PayoutIdentity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new PayoutApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        PayoutInstructionEntity p = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new PayoutApiException(HttpStatus.NOT_FOUND, "PAYOUT_NOT_FOUND", "Payout not found"));

        if (req.getAction() == FallbackAction.USE_CASH_OUT && !"CASH_OUT_AGENT".equals(p.getRoute())) {
            throw new PayoutApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ROUTE_NOT_AUTHORIZED",
                    "Wallet payout cannot be converted to cash-out via fallback without farmer consent");
        }
        if (p.getFallbackCount() >= properties.getRules().getMaxFallbackSwitches()) {
            throw new PayoutApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FALLBACK_LIMIT_REACHED",
                    "Maximum fallback switches reached");
        }

        if (p.getProviderRef() != null) {
            String outcome = clients.queryProviderStatus(p.getProviderRef());
            if ("UNKNOWN".equals(outcome)) {
                throw new PayoutApiException(HttpStatus.CONFLICT, "OUTCOME_UNCERTAIN",
                        "Provider status uncertain; wait for resolution before switching");
            }
            if ("COMPLETED".equals(outcome)) {
                throw new PayoutApiException(HttpStatus.CONFLICT, "ALREADY_COMPLETED",
                        "Original payout already completed at provider");
            }
        }
        if (!clients.fetchPaymentInstruction(p.getClaimId()).payable()) {
            throw new PayoutApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CLAIM_NOT_PAYABLE", "Claim no longer payable");
        }

        Instant now = Instant.now();
        String newConn = req.getTargetConnection() != null && !req.getTargetConnection().isBlank()
                ? req.getTargetConnection() : "MPESA_SECONDARY";
        PayoutExternalClients.ProviderDispatchResult res = clients.dispatchToProvider(
                newConn, p.getOriginatorRef(), p.getAmountMinor(), p.getDestinationToken());

        List<Map<String, Object>> attempts = new ArrayList<>(readList(p.getAttemptsJson()));
        attempts.add(Map.of("connection", newConn, "fallback", true, "reason", req.getReason(), "at", now.toString()));

        p.setConnection(newConn);
        p.setProviderRef(res.providerRef());
        p.setProviderRefMasked(clients.maskProviderRef(res.providerRef()));
        p.setStatus(res.accepted() ? PayoutStatus.PENDING : PayoutStatus.FAILED);
        p.setFallbackCount(p.getFallbackCount() + 1);
        p.setAttemptsJson(writeJson(attempts));
        p.setVersionNum(p.getVersionNum() + 1);
        payoutRepository.save(p);
        return getPayout(payoutId);
    }

    public PayoutListResponse listPayouts(String queue, String status, String claimId, int page, int size) {
        PayoutIdentity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.FINANCE, AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR, AuthRole.ADMIN)) {
            throw new PayoutApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        List<PayoutInstructionEntity> all = payoutRepository.findAll().stream()
                .filter(p -> status == null || p.getStatus().name().equalsIgnoreCase(status))
                .filter(p -> claimId == null || claimId.equals(p.getClaimId()))
                .filter(p -> matchesQueue(p, queue))
                .sorted(Comparator.comparing(PayoutInstructionEntity::getCreatedAt).reversed())
                .toList();

        int fromIdx = Math.min(page * size, all.size());
        int toIdx = Math.min(fromIdx + size, all.size());
        List<Map<String, Object>> items = all.subList(fromIdx, toIdx).stream()
                .map(p -> Map.<String, Object>of(
                        "payoutId", p.getPayoutId(), "claimId", p.getClaimId(), "attempt", p.getAttempt(),
                        "status", p.getStatus().name(), "route", p.getRoute(),
                        "amountMinor", p.getAmountMinor(), "currency", p.getCurrency()))
                .toList();

        Map<String, Long> counts = new LinkedHashMap<>();
        all.forEach(p -> counts.merge(p.getStatus().name(), 1L, Long::sum));
        List<Map<String, Object>> provs = connectionRepository.findAll().stream()
                .map(c -> Map.<String, Object>of("connectionId", c.getConnectionId(),
                        "provider", c.getProvider(), "status", c.getStatus().name()))
                .toList();

        return PayoutListResponse.builder().items(items).counts(counts).providers(provs)
                .page(page).size(size).total(all.size()).build();
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void chasePendingPayouts() {
        Instant now = Instant.now();
        for (PayoutInstructionEntity p : payoutRepository.findByStatusIn(List.of(PayoutStatus.PENDING, PayoutStatus.SENDING))) {
            if (!p.isEscalated() && p.getCreatedAt().plus(properties.getRules().getEscalationHours(), ChronoUnit.HOURS).isBefore(now)) {
                p.setEscalated(true);
                p.setVersionNum(p.getVersionNum() + 1);
                payoutRepository.save(p);
                outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.escalated", p.getClaimId(), Map.of(
                        "eventType", "payout.escalated", "payoutId", p.getPayoutId(), "claimId", p.getClaimId(),
                        "timestamp", now.toString()));
            }
            if ("CASH_OUT_AGENT".equals(p.getRoute()) && "ISSUED".equals(p.getVoucherStatus())
                    && p.getVoucherExpiresAt() != null && p.getVoucherExpiresAt().isBefore(now)) {
                p.setVoucherStatus("EXPIRED");
                p.setStatus(PayoutStatus.FAILED);
                p.setReasonCode("CASHOUT_NOT_COLLECTED");
                p.setCompletedAt(now);
                payoutRepository.save(p);
                outboxService.enqueue(properties.getKafka().getPayoutTopic(), "payout.failed", p.getClaimId(), Map.of(
                        "eventType", "payout.failed", "payoutId", p.getPayoutId(), "claimId", p.getClaimId(),
                        "reasonCode", "CASHOUT_NOT_COLLECTED", "timestamp", now.toString()));
            }
        }
    }

    private boolean matchesQueue(PayoutInstructionEntity p, String queue) {
        if (queue == null || queue.isBlank()) return true;
        return switch (queue.toUpperCase()) {
            case "FAILED" -> p.getStatus() == PayoutStatus.FAILED;
            case "ESCALATED" -> p.isEscalated();
            case "WAITING_PROVIDER" -> p.getStatus() == PayoutStatus.WAITING_PROVIDER;
            case "CASHOUT_OPEN" -> "CASH_OUT_AGENT".equals(p.getRoute()) && "ISSUED".equals(p.getVoucherStatus());
            default -> true;
        };
    }

    private PayoutIdentity requireIdentity() {
        PayoutIdentity id = PayoutIdentityFilter.CTX.get();
        if (id == null) throw new PayoutApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
        return id;
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "[]"; }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return List.of(); }
    }
}
