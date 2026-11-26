package com.umar.claims.service;

import com.umar.claims.client.ClaimsExternalClients;
import com.umar.claims.config.ClaimsProperties;
import com.umar.claims.domain.*;
import com.umar.claims.repository.*;
import com.umar.claims.service.support.ClaimsOutboxService;
import com.umar.enums.claims.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClaimsSagaService {

    private final ClaimRepository claimRepository;
    private final ClaimReservationRepository reservationRepository;
    private final OverpaymentCaseRepository overpaymentRepository;
    private final ClaimsInboxEventRepository inboxRepository;
    private final ClaimsExternalClients clients;
    private final ClaimsOutboxService outboxService;
    private final ClaimsProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public ClaimEntity onThresholdBreached(String eventId, String decisionId, String policyId,
                                           String ruleId, Integer ruleVersion, String windowId,
                                           String tierId, double payoutPercent, Instant decidedAt) {
        if (eventId != null && inboxRepository.existsById(eventId)) {
            return claimRepository.findByPolicyIdAndWindowIdAndTierIdAndType(policyId, windowId, tierId, ClaimType.PAYOUT)
                    .orElse(null);
        }
        if (eventId != null) {
            inboxRepository.save(ClaimsInboxEventEntity.builder()
                    .eventId(eventId).eventType("threshold.breached").processedAt(Instant.now()).build());
        }

        Optional<ClaimEntity> existing = claimRepository.findByPolicyIdAndWindowIdAndTierIdAndType(
                policyId, windowId, tierId, ClaimType.PAYOUT);
        if (existing.isPresent()) return existing.get();

        ClaimsExternalClients.TriggerStatusView trig = clients.fetchTriggerStatus(decisionId);
        if (!trig.finalBreach()) return null;

        ClaimsExternalClients.PolicyTermsView terms = clients.fetchPolicyTerms(policyId);
        if (!terms.active()) return null;

        long openRes = claimRepository.findByPolicyIdAndStatusIn(policyId, List.of(
                ClaimStatus.OPENED, ClaimStatus.VALIDATED, ClaimStatus.ON_HOLD, ClaimStatus.APPROVED, ClaimStatus.PAYING))
                .stream().mapToLong(ClaimEntity::getPayableMinor).sum();
        long calculated = Math.round(terms.sumInsuredMinor() * (payoutPercent / 100.0));
        long remaining = Math.max(0L, terms.sumInsuredMinor() - terms.paidToDateMinor() - openRes);
        long payable = Math.min(calculated, remaining);
        if (payable <= 0) return null;

        List<String> holdReasons = new ArrayList<>();
        if (clients.isAccountFrozen(terms.farmerId())) holdReasons.add("ACCOUNT_FROZEN");
        if (!clients.hasAutomatedPayoutConsent(terms.farmerId())) holdReasons.add("FARMER_CONFIRMATION_REQUIRED");
        if (clients.isFraudFlagged(policyId)) holdReasons.add("FRAUD_SIGNAL");

        ClaimStatus initialStatus;
        ApprovalPath path = null;
        Instant approvedAt = null;
        if (!holdReasons.isEmpty()) {
            initialStatus = ClaimStatus.ON_HOLD;
        } else if (payable <= properties.getRules().getAutoApprovalLimitMinor()) {
            initialStatus = ClaimStatus.APPROVED;
            path = ApprovalPath.AUTO;
            approvedAt = Instant.now();
        } else {
            initialStatus = ClaimStatus.VALIDATED;
        }

        String claimId = "clm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String claimNumber = "CLM-" + claimId.substring(4).toUpperCase();
        Instant now = Instant.now();
        String claimHash = computeClaimHash(claimId, payable, initialStatus.name(), holdReasons);

        List<Map<String, Object>> timeline = List.of(Map.of(
                "event", "OPENED", "at", now.toString(), "status", initialStatus.name()));

        ClaimEntity claim = claimRepository.save(ClaimEntity.builder()
                .claimId(claimId)
                .claimNumber(claimNumber)
                .type(ClaimType.PAYOUT)
                .policyId(policyId)
                .farmerId(terms.farmerId())
                .plotId(terms.plotId())
                .regionCode(terms.regionCode())
                .ruleId(ruleId)
                .ruleVersion(ruleVersion)
                .windowId(windowId)
                .tierId(tierId)
                .decisionId(decisionId)
                .status(initialStatus)
                .approvalPath(path)
                .calculatedMinor(calculated)
                .payableMinor(payable)
                .currency(terms.currency())
                .termsHash(terms.termsHash())
                .claimHash(claimHash)
                .holdReasonsCsv(String.join(",", holdReasons))
                .nextStep(initialStatus == ClaimStatus.APPROVED ? NextStepAction.WAIT_FOR_PAYMENT : NextStepAction.NONE)
                .currentAttempt(initialStatus == ClaimStatus.APPROVED ? 1 : 0)
                .maxAttempts(properties.getRules().getMaxPayoutAttempts())
                .timelineJson(writeJson(timeline))
                .openedAt(now)
                .approvedAt(approvedAt)
                .versionNum(1)
                .build());

        reservationRepository.save(ClaimReservationEntity.builder()
                .reservationId("res_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .claimId(claimId)
                .policyId(policyId)
                .amountMinor(payable)
                .status("RESERVED")
                .createdAt(now)
                .updatedAt(now)
                .build());

        outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.opened", claimId, Map.of(
                "eventType", "claim.opened", "claimId", claimId, "claimNumber", claimNumber,
                "policyId", policyId, "farmerId", terms.farmerId(), "amountMinor", payable,
                "currency", terms.currency(), "decisionId", decisionId,
                "decisionAt", (decidedAt != null ? decidedAt : now).toString(), "timestamp", now.toString()));

        if (initialStatus == ClaimStatus.APPROVED) {
            outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.approved", claimId, Map.of(
                    "eventType", "claim.approved", "claimId", claimId, "policyId", policyId,
                    "farmerId", terms.farmerId(), "amountMinor", payable,
                    "approvalPath", ApprovalPath.AUTO.name(), "attempt", 1,
                    "decisionId", decisionId, "termsHash", terms.termsHash(), "timestamp", now.toString()));
        } else if (initialStatus == ClaimStatus.ON_HOLD) {
            outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.held", claimId, Map.of(
                    "eventType", "claim.held", "claimId", claimId, "policyId", policyId,
                    "farmerId", terms.farmerId(), "holdReasons", holdReasons,
                    "amountMinor", payable, "timestamp", now.toString()));
        } else {
            outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.validated", claimId, Map.of(
                    "eventType", "claim.validated", "claimId", claimId, "policyId", policyId,
                    "amountMinor", payable, "timestamp", now.toString()));
        }
        return claim;
    }

    @Transactional
    public ClaimEntity onPayoutEvent(String eventId, String eventType, String claimId, String payoutId,
                                     long amountMinor, String reasonCode, String nextStepHint) {
        if (eventId != null && inboxRepository.existsById(eventId)) {
            return claimRepository.findById(claimId).orElse(null);
        }
        if (eventId != null) {
            inboxRepository.save(ClaimsInboxEventEntity.builder()
                    .eventId(eventId).eventType(eventType).processedAt(Instant.now()).build());
        }
        ClaimEntity claim = claimRepository.findById(claimId).orElse(null);
        if (claim == null) return null;

        Instant now = Instant.now();
        switch (eventType) {
            case "payout.initiated" -> {
                claim.setStatus(ClaimStatus.PAYING);
                claim.setPayoutId(payoutId);
                claim.setVersionNum(claim.getVersionNum() + 1);
            }
            case "payout.completed" -> {
                if (claim.getStatus() == ClaimStatus.PAID) return claim;
                claim.setStatus(ClaimStatus.PAID);
                claim.setPayoutId(payoutId);
                claim.setPaidAt(now);
                claim.setDisputeWindowEnds(now.plus(properties.getRules().getDisputeWindowDays(), ChronoUnit.DAYS));
                double hrs = Duration.between(claim.getOpenedAt(), now).toSeconds() / 3600.0;
                claim.setHoursToPay(hrs);
                claim.setNextStep(NextStepAction.NONE);
                claim.setVersionNum(claim.getVersionNum() + 1);
                reservationRepository.findByClaimId(claimId).ifPresent(r -> {
                    r.setStatus("PAID");
                    r.setUpdatedAt(now);
                    reservationRepository.save(r);
                });
                outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.paid", claimId, Map.of(
                        "eventType", "claim.paid", "claimId", claimId, "payoutId", Objects.toString(payoutId, ""),
                        "farmerId", claim.getFarmerId(), "amountMinor", amountMinor > 0 ? amountMinor : claim.getPayableMinor(),
                        "hoursToPay", hrs, "paidAt", now.toString()));
            }
            case "payout.failed" -> {
                if (claim.getCurrentAttempt() >= claim.getMaxAttempts()) {
                    claim.setStatus(ClaimStatus.ON_HOLD);
                    List<String> holds = parseHolds(claim.getHoldReasonsCsv());
                    if (!holds.contains("SYSTEM_RETRY_EXHAUSTED")) holds.add("SYSTEM_RETRY_EXHAUSTED");
                    claim.setHoldReasonsCsv(String.join(",", holds));
                    claim.setNextStep(NextStepAction.NONE);
                } else {
                    claim.setStatus(ClaimStatus.FAILED);
                    NextStepAction ns = NextStepAction.SUPPORT_RETRY;
                    if (nextStepHint != null) {
                        try { ns = NextStepAction.valueOf(nextStepHint); } catch (Exception ignored) {}
                    }
                    claim.setNextStep(ns);
                }
                claim.setVersionNum(claim.getVersionNum() + 1);
                outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.failed", claimId, Map.of(
                        "eventType", "claim.failed", "claimId", claimId, "farmerId", claim.getFarmerId(),
                        "nextStep", claim.getNextStep().name(),
                        "reasonCode", Objects.toString(reasonCode, "PAYOUT_FAILED"), "timestamp", now.toString()));
            }
            case "payout.escalated" -> {
                claim.setNextStep(NextStepAction.CHECK_PENDING);
                claim.setVersionNum(claim.getVersionNum() + 1);
            }
        }
        claim.setClaimHash(computeClaimHash(claim.getClaimId(), claim.getPayableMinor(), claim.getStatus().name(),
                parseHolds(claim.getHoldReasonsCsv())));
        return claimRepository.save(claim);
    }

    @Transactional
    public void onTriggerDecisionRevised(String eventId, String decisionId, double revisedPayoutPercent) {
        if (eventId != null && inboxRepository.existsById(eventId)) return;
        if (eventId != null) {
            inboxRepository.save(ClaimsInboxEventEntity.builder()
                    .eventId(eventId).eventType("trigger.decision.revised").processedAt(Instant.now()).build());
        }
        Instant now = Instant.now();
        for (ClaimEntity c : claimRepository.findByDecisionId(decisionId)) {
            ClaimsExternalClients.PolicyTermsView terms = clients.fetchPolicyTerms(c.getPolicyId());
            long newPayable = Math.round(terms.sumInsuredMinor() * (revisedPayoutPercent / 100.0));
            if (c.getStatus() == ClaimStatus.PAID || c.getStatus() == ClaimStatus.CLOSED) {
                if (newPayable < c.getPayableMinor()) {
                    long excess = c.getPayableMinor() - newPayable;
                    String caseId = "ovp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                    overpaymentRepository.save(OverpaymentCaseEntity.builder()
                            .caseId(caseId).claimId(c.getClaimId()).policyId(c.getPolicyId())
                            .paidMinor(c.getPayableMinor()).revisedPayableMinor(newPayable)
                            .excessMinor(excess).reasonCode("DECISION_REVISED").status("OPEN").createdAt(now).build());
                    outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.overpayment.detected", c.getClaimId(), Map.of(
                            "eventType", "claim.overpayment.detected", "caseId", caseId, "claimId", c.getClaimId(),
                            "excessMinor", excess, "timestamp", now.toString()));
                }
            } else if (c.getStatus() == ClaimStatus.PAYING) {
                c.setCancelRequested(true);
                c.setPayableMinor(newPayable);
                claimRepository.save(c);
                outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.payout.cancel.requested", c.getClaimId(), Map.of(
                        "eventType", "claim.payout.cancel.requested", "claimId", c.getClaimId(),
                        "payoutId", Objects.toString(c.getPayoutId(), ""), "reasonCode", "DECISION_REVISED",
                        "timestamp", now.toString()));
            } else if (newPayable == 0) {
                c.setStatus(ClaimStatus.VOIDED);
                c.setPayableMinor(0L);
                claimRepository.save(c);
                reservationRepository.findByClaimId(c.getClaimId()).ifPresent(r -> {
                    r.setStatus("RELEASED");
                    r.setUpdatedAt(now);
                    reservationRepository.save(r);
                });
                outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.voided", c.getClaimId(), Map.of(
                        "eventType", "claim.voided", "claimId", c.getClaimId(), "releasedMinor", c.getCalculatedMinor(),
                        "timestamp", now.toString()));
            } else {
                c.setPayableMinor(newPayable);
                claimRepository.save(c);
            }
        }
    }

    @Transactional
    public ClaimEntity onPolicyExpired(String eventId, String policyId) {
        if (eventId != null && inboxRepository.existsById(eventId)) return null;
        if (eventId != null) {
            inboxRepository.save(ClaimsInboxEventEntity.builder()
                    .eventId(eventId).eventType("policy.expired").processedAt(Instant.now()).build());
        }
        Instant now = Instant.now();
        List<ClaimEntity> existing = claimRepository.findByPolicyId(policyId);
        if (existing.isEmpty()) {
            ClaimsExternalClients.PolicyTermsView terms = clients.fetchPolicyTerms(policyId);
            String claimId = "clm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            String claimNumber = "CLM-" + claimId.substring(4).toUpperCase();
            ClaimEntity rec = claimRepository.save(ClaimEntity.builder()
                    .claimId(claimId).claimNumber(claimNumber).type(ClaimType.NO_PAYOUT_RECORD)
                    .policyId(policyId).farmerId(terms.farmerId()).plotId(terms.plotId()).regionCode(terms.regionCode())
                    .windowId("EXPIRY").tierId("NONE")
                    .status(ClaimStatus.CLOSED).calculatedMinor(0).payableMinor(0).currency(terms.currency())
                    .claimHash(computeClaimHash(claimId, 0, "CLOSED", List.of()))
                    .timelineJson("[]").openedAt(now).closedAt(now)
                    .disputeWindowEnds(now.plus(properties.getRules().getDisputeWindowDays(), ChronoUnit.DAYS))
                    .versionNum(1).build());
            outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.closed", claimId, Map.of(
                    "eventType", "claim.closed", "claimId", claimId, "policyId", policyId,
                    "type", ClaimType.NO_PAYOUT_RECORD.name(), "disputeWindowEnds", rec.getDisputeWindowEnds().toString(),
                    "timestamp", now.toString()));
            return rec;
        }
        return null;
    }

    public String computeClaimHash(String claimId, long payableMinor, String status, List<String> holds) {
        return clients.sha256(claimId + "|" + payableMinor + "|" + status + "|" + String.join(",", holds));
    }

    private List<String> parseHolds(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "[]"; }
    }
}
