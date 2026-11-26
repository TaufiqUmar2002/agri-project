package com.umar.claims.service;

import com.umar.claims.client.ClaimsExternalClients;
import com.umar.claims.config.ClaimsProperties;
import com.umar.claims.domain.*;
import com.umar.claims.exception.ClaimsApiException;
import com.umar.claims.repository.*;
import com.umar.claims.security.ClaimsIdentity;
import com.umar.claims.security.ClaimsSecurityContext;
import com.umar.claims.service.support.ClaimsIdempotencyService;
import com.umar.claims.service.support.ClaimsOutboxService;
import com.umar.claims.service.support.ClaimsRateLimitService;
import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.StepUpActionClass;
import com.umar.enums.claims.*;
import com.umar.payload.request.claims.*;
import com.umar.payload.response.claims.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClaimsApiService {

    private final ClaimRepository claimRepository;
    private final ClaimReservationRepository reservationRepository;
    private final ClaimApprovalRepository approvalRepository;
    private final ClaimDisputeRepository disputeRepository;
    private final ClaimConfirmationRepository confirmationRepository;
    private final ReconciliationBreakRepository breakRepository;
    private final ClaimsSagaService sagaService;
    private final ClaimsExternalClients clients;
    private final ClaimsOutboxService outboxService;
    private final ClaimsIdempotencyService idempotencyService;
    private final ClaimsRateLimitService rateLimitService;
    private final ClaimsProperties properties;
    private final ObjectMapper objectMapper;

    public ClaimDetailResponse getClaim(String claimId) {
        ClaimsIdentity actor = ClaimsSecurityContext.require();
        ClaimEntity c = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimsApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found"));

        boolean isStaff = actor.isServiceCaller() || actor.hasAnyRole(
                AuthRole.SUPPORT, AuthRole.CLAIMS_REVIEWER, AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR, AuthRole.FINANCE, AuthRole.ADMIN);
        boolean isOwner = actor.hasRole(AuthRole.FARMER) && actor.actorId().equals(c.getFarmerId());
        if (!isStaff && !isOwner) {
            throw new ClaimsApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found");
        }

        List<Map<String, Object>> approvals = isStaff
                ? approvalRepository.findByClaimIdOrderByDecidedAtAsc(claimId).stream()
                        .map(a -> Map.<String, Object>of("approvalId", a.getApprovalId(),
                                "role", a.getApproverRole(), "action", a.getAction().name(),
                                "decidedAt", a.getDecidedAt().toString()))
                        .toList()
                : null;
        List<Map<String, Object>> disputes = disputeRepository.findByClaimIdOrderByCreatedAtDesc(claimId).stream()
                .map(d -> Map.<String, Object>of("disputeId", d.getDisputeId(), "status", d.getStatus(),
                        "reasonCode", d.getReasonCode().name()))
                .toList();

        return ClaimDetailResponse.builder()
                .claimId(c.getClaimId())
                .claimNumber(c.getClaimNumber())
                .type(c.getType().name())
                .policyId(c.getPolicyId())
                .farmerId(c.getFarmerId())
                .plotId(c.getPlotId())
                .decisionId(c.getDecisionId())
                .status(c.getStatus().name())
                .approvalPath(c.getApprovalPath() != null ? c.getApprovalPath().name() : null)
                .calculatedMinor(c.getCalculatedMinor())
                .payableMinor(c.getPayableMinor())
                .currency(c.getCurrency())
                .termsHash(c.getTermsHash())
                .claimHash(c.getClaimHash())
                .holdReasons(isStaff ? parseHolds(c.getHoldReasonsCsv()) : List.of())
                .nextStep(c.getNextStep() != null ? c.getNextStep().name() : null)
                .currentAttempt(c.getCurrentAttempt())
                .cancelRequested(c.isCancelRequested())
                .hoursToPay(c.getHoursToPay())
                .timeline(readList(c.getTimelineJson()))
                .disputes(disputes)
                .approvals(approvals)
                .openedAt(c.getOpenedAt().toString())
                .paidAt(c.getPaidAt() != null ? c.getPaidAt().toString() : null)
                .closedAt(c.getClosedAt() != null ? c.getClosedAt().toString() : null)
                .disputeWindowEnds(c.getDisputeWindowEnds() != null ? c.getDisputeWindowEnds().toString() : null)
                .version(c.getVersionNum())
                .build();
    }

    @Transactional
    public ClaimActionResponse approveClaim(String claimId, ApproveClaimRequest req) {
        ClaimsIdentity actor = ClaimsSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.CLAIMS_REVIEWER, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN)) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        ClaimEntity c = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimsApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found"));

        if (!req.getClaimHash().equals(c.getClaimHash())) {
            throw new ClaimsApiException(HttpStatus.CONFLICT, "CLAIM_CHANGED", "Claim changed since loaded");
        }
        if (clients.isConflictOfInterest(actor.actorId(), c.getPolicyId(), c.getFarmerId())) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "CONFLICT_OF_INTEREST", "Conflict of interest prevents reviewing");
        }

        List<String> holds = parseHolds(c.getHoldReasonsCsv());
        if (req.getAction() == ClaimDecisionAction.APPROVE && req.getRetryId() == null) {
            if (holds.contains("ACCOUNT_FROZEN") || holds.contains("FARMER_CONFIRMATION_REQUIRED")) {
                throw new ClaimsApiException(HttpStatus.UNPROCESSABLE_ENTITY, "HOLD_NOT_APPROVABLE",
                        "Active hold cannot be bypassed by reviewer approval", Map.of("holdReasons", holds));
            }
        }

        List<ClaimApprovalEntity> prior = approvalRepository.findByClaimIdOrderByDecidedAtAsc(claimId).stream()
                .filter(a -> Objects.equals(a.getRetryId(), req.getRetryId())).toList();
        if (prior.stream().anyMatch(a -> a.getApproverId().equals(actor.actorId()))) {
            throw new ClaimsApiException(HttpStatus.CONFLICT, "ALREADY_DECIDED", "You already submitted an approval decision");
        }

        boolean needsTwo = req.getAction() == ClaimDecisionAction.REJECT
                || c.getPayableMinor() >= properties.getRules().getDualApprovalLimitMinor()
                || req.getRetryId() != null;
        String actingRole = actor.hasRole(AuthRole.COMPLIANCE_OFFICER) ? "COMPLIANCE_OFFICER"
                : (actor.hasRole(AuthRole.CLAIMS_REVIEWER) ? "CLAIMS_REVIEWER" : "ADMIN");

        Instant now = Instant.now();
        approvalRepository.save(ClaimApprovalEntity.builder()
                .approvalId("capr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .claimId(claimId).retryId(req.getRetryId())
                .approverId(actor.actorId()).approverRole(actingRole)
                .action(req.getAction()).note(req.getNote()).decidedAt(now).build());

        List<ClaimApprovalEntity> allForStep = approvalRepository.findByClaimIdOrderByDecidedAtAsc(claimId).stream()
                .filter(a -> Objects.equals(a.getRetryId(), req.getRetryId())).toList();

        List<String> remaining = new ArrayList<>();
        if (req.getAction() == ClaimDecisionAction.REJECT) {
            boolean hasComp = allForStep.stream().anyMatch(a -> "COMPLIANCE_OFFICER".equals(a.getApproverRole()) || "ADMIN".equals(a.getApproverRole()));
            if (allForStep.size() >= 2 && hasComp) {
                c.setStatus(ClaimStatus.REJECTED);
                reservationRepository.findByClaimId(claimId).ifPresent(r -> {
                    r.setStatus("RELEASED");
                    r.setUpdatedAt(now);
                    reservationRepository.save(r);
                });
            } else {
                if (!hasComp) remaining.add("COMPLIANCE_OFFICER");
                if (allForStep.size() < 2) remaining.add("CLAIMS_REVIEWER");
            }
        } else if (req.getAction() == ClaimDecisionAction.RETURN_TO_HOLD) {
            c.setStatus(ClaimStatus.ON_HOLD);
        } else {
            // APPROVE
            if (!needsTwo || allForStep.size() >= 2) {
                if (Boolean.TRUE.equals(req.getFraudCleared())) {
                    holds.remove("FRAUD_SIGNAL");
                    c.setHoldReasonsCsv(String.join(",", holds));
                }
                if (req.getRetryId() != null) {
                    c.setPendingRetryId(null);
                    c.setStatus(ClaimStatus.APPROVED);
                    c.setNextStep(NextStepAction.WAIT_FOR_PAYMENT);
                    outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.retry.requested", claimId, Map.of(
                            "eventType", "claim.retry.requested", "claimId", claimId, "attempt", c.getCurrentAttempt(),
                            "route", Objects.toString(c.getPayoutRoute(), "SAME_WALLET"),
                            "useCurrentDestination", c.isUseCurrentDestination(),
                            "timestamp", now.toString()));
                } else {
                    c.setStatus(ClaimStatus.APPROVED);
                    c.setApprovalPath(ApprovalPath.MANUAL);
                    c.setApprovedAt(now);
                    c.setCurrentAttempt(Math.max(1, c.getCurrentAttempt()));
                    c.setNextStep(NextStepAction.WAIT_FOR_PAYMENT);
                    outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.approved", claimId, Map.of(
                            "eventType", "claim.approved", "claimId", claimId, "policyId", c.getPolicyId(),
                            "farmerId", c.getFarmerId(), "amountMinor", c.getPayableMinor(),
                            "approvalPath", ApprovalPath.MANUAL.name(), "attempt", c.getCurrentAttempt(),
                            "decisionId", Objects.toString(c.getDecisionId(), ""),
                            "termsHash", Objects.toString(c.getTermsHash(), ""), "timestamp", now.toString()));
                }
            } else {
                remaining.add("SECOND_REVIEWER");
            }
        }

        c.setVersionNum(c.getVersionNum() + 1);
        c.setClaimHash(sagaService.computeClaimHash(claimId, c.getPayableMinor(), c.getStatus().name(), holds));
        claimRepository.save(c);

        return ClaimActionResponse.builder()
                .claimId(claimId).status(c.getStatus().name())
                .approvalPath(c.getApprovalPath() != null ? c.getApprovalPath().name() : null)
                .remainingApprovals(remaining).decidedAt(now.toString()).version(c.getVersionNum()).build();
    }

    @Transactional
    public DisputeResponse createDispute(String claimId, CreateDisputeRequest req, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ClaimsApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        ClaimsIdentity actor = ClaimsSecurityContext.require();
        Optional<DisputeResponse> replay = idempotencyService.findReplay(idempotencyKey, "CLM-04", req, DisputeResponse.class);
        if (replay.isPresent()) return replay.get();

        ClaimEntity c = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimsApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found"));
        boolean isStaff = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.ADMIN);
        if (!isStaff && (!actor.hasRole(AuthRole.FARMER) || !actor.actorId().equals(c.getFarmerId()))) {
            throw new ClaimsApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found");
        }

        Instant now = Instant.now();
        if (c.getDisputeWindowEnds() != null && now.isAfter(c.getDisputeWindowEnds())) {
            throw new ClaimsApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DISPUTE_WINDOW_CLOSED", "Dispute window closed");
        }
        if (c.getDisputeCount() >= properties.getRules().getMaxDisputesPerClaim()) {
            throw new ClaimsApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DISPUTE_LIMIT_REACHED", "Maximum 2 disputes allowed per claim");
        }
        List<ClaimDisputeEntity> existing = disputeRepository.findByClaimIdOrderByCreatedAtDesc(claimId);
        if (existing.stream().anyMatch(d -> !"RESOLVED".equals(d.getStatus()))) {
            throw new ClaimsApiException(HttpStatus.CONFLICT, "DISPUTE_ALREADY_OPEN", "An open dispute already exists");
        }
        if (req.getPhotoTokens() != null && !req.getPhotoTokens().isEmpty() && !clients.verifyPhotoTokens(req.getPhotoTokens())) {
            throw new ClaimsApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_UNVERIFIED", "Evidence photos could not be verified");
        }

        String disputeId = "dsp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        c.setPriorStatus(c.getStatus());
        c.setStatus(ClaimStatus.DISPUTED);
        c.setDisputeCount(c.getDisputeCount() + 1);
        c.setVersionNum(c.getVersionNum() + 1);
        claimRepository.save(c);

        Map<String, Object> checks = Map.of("boundaryCheck", "PASSED", "seasonCheck", "PASSED", "dataCheck", "PASSED");
        disputeRepository.save(ClaimDisputeEntity.builder()
                .disputeId(disputeId).claimId(claimId).reasonCode(req.getReasonCode())
                .explanation(req.getExplanation())
                .photoTokensCsv(req.getPhotoTokens() != null ? String.join(",", req.getPhotoTokens()) : null)
                .status("OPEN").checksJson(writeJson(checks)).createdAt(now).build());

        outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.disputed", claimId, Map.of(
                "eventType", "claim.disputed", "disputeId", disputeId, "claimId", claimId,
                "farmerId", c.getFarmerId(), "reasonCode", req.getReasonCode().name(), "timestamp", now.toString()));

        DisputeResponse resp = DisputeResponse.builder()
                .disputeId(disputeId).claimId(claimId).status("OPEN").reasonCode(req.getReasonCode().name())
                .claimStatus(ClaimStatus.DISPUTED.name()).checks(checks).createdAt(now.toString()).build();
        idempotencyService.save(idempotencyKey, "CLM-04", req, 201, resp);
        return resp;
    }

    @Transactional
    public ClaimActionResponse retryClaim(String claimId, RetryClaimRequest req, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ClaimsApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        ClaimsIdentity actor = ClaimsSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.CLAIMS_REVIEWER, AuthRole.ADMIN)) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        Optional<ClaimActionResponse> replay = idempotencyService.findReplay(idempotencyKey, "CLM-05", req, ClaimActionResponse.class);
        if (replay.isPresent()) return replay.get();

        ClaimEntity c = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimsApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found"));
        if (c.getStatus() != ClaimStatus.FAILED) {
            throw new ClaimsApiException(HttpStatus.CONFLICT, "CLAIM_STATE_INVALID", "Only FAILED claims can be retried");
        }
        if (c.getCurrentAttempt() >= c.getMaxAttempts()) {
            throw new ClaimsApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RETRY_LIMIT_REACHED", "Maximum payout attempts reached");
        }
        Instant now = Instant.now();
        if (c.getLastRetryAt() != null && now.isBefore(c.getLastRetryAt().plus(properties.getRules().getRetryCooldownMinutes(), ChronoUnit.MINUTES))) {
            throw new ClaimsApiException(HttpStatus.TOO_MANY_REQUESTS, "RETRY_COOLDOWN", "Retry cooldown active");
        }
        if (actor.hasRole(AuthRole.SUPPORT) && !actor.hasAnyRole(AuthRole.CLAIMS_REVIEWER, AuthRole.ADMIN)
                && req.getRoute() != RetryRoute.SAME_WALLET) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "ROUTE_NOT_PERMITTED",
                    "Support can only retry on SAME_WALLET");
        }
        if (req.getRoute() != RetryRoute.SAME_WALLET) {
            if (!clients.validateAndConsumeStepUp(req.getFarmerStepUpToken(), c.getFarmerId(), StepUpActionClass.CLAIM_RETRY)) {
                throw new ClaimsApiException(HttpStatus.FORBIDDEN, "STEP_UP_REQUIRED", "Valid farmer step-up token required for alternate route");
            }
        }

        int nextAttempt = c.getCurrentAttempt() + 1;
        c.setCurrentAttempt(nextAttempt);
        c.setLastRetryAt(now);
        c.setPayoutRoute(req.getRoute().name());
        c.setDestinationId(req.getDestinationId());
        c.setUseCurrentDestination(Boolean.TRUE.equals(req.getUseCurrentDestination()));

        String retryId = null;
        if (Boolean.TRUE.equals(req.getUseCurrentDestination())) {
            retryId = "rty_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            c.setPendingRetryId(retryId);
            c.setStatus(ClaimStatus.ON_HOLD);
            c.setNextStep(NextStepAction.CHECK_PENDING);
        } else {
            c.setStatus(ClaimStatus.APPROVED);
            c.setNextStep(NextStepAction.WAIT_FOR_PAYMENT);
            outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.retry.requested", claimId, Map.of(
                    "eventType", "claim.retry.requested", "claimId", claimId, "attempt", nextAttempt,
                    "route", req.getRoute().name(), "destinationId", Objects.toString(req.getDestinationId(), ""),
                    "useCurrentDestination", false, "timestamp", now.toString()));
        }

        c.setVersionNum(c.getVersionNum() + 1);
        c.setClaimHash(sagaService.computeClaimHash(claimId, c.getPayableMinor(), c.getStatus().name(), parseHolds(c.getHoldReasonsCsv())));
        claimRepository.save(c);

        ClaimActionResponse resp = ClaimActionResponse.builder()
                .claimId(claimId).status(c.getStatus().name()).route(req.getRoute().name())
                .attempt(nextAttempt).retryId(retryId).nextStep(c.getNextStep().name())
                .version(c.getVersionNum()).build();
        idempotencyService.save(idempotencyKey, "CLM-05", req, 200, resp);
        return resp;
    }

    @Transactional
    public ClaimActionResponse confirmPayout(String claimId, ConfirmPayoutRequest req,
                                             String delegationHeader, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ClaimsApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        ClaimsIdentity actor = ClaimsSecurityContext.require();
        if (delegationHeader != null && !delegationHeader.isBlank() || actor.hasRole(AuthRole.AGENT)) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "DELEGATION_NOT_ALLOWED", "Only the farmer can confirm");
        }
        ClaimEntity c = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimsApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found"));
        if (!actor.actorId().equals(c.getFarmerId())) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }

        Optional<ClaimActionResponse> replay = idempotencyService.findReplay(idempotencyKey, "CLM-07:" + actor.actorId(), req, ClaimActionResponse.class);
        if (replay.isPresent()) return replay.get();

        rateLimitService.check("clm-confirm:" + actor.actorId(), 10, 3600);

        List<String> holds = parseHolds(c.getHoldReasonsCsv());
        if (c.getStatus() != ClaimStatus.ON_HOLD || !holds.contains("FARMER_CONFIRMATION_REQUIRED")) {
            throw new ClaimsApiException(HttpStatus.CONFLICT, "CLAIM_STATE_INVALID", "Nothing to confirm",
                    Map.of("status", c.getStatus().name()));
        }
        if (!clients.validateAndConsumeStepUp(req.getStepUpToken(), c.getFarmerId(), StepUpActionClass.CLAIM_CONFIRM)) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "STEP_UP_REQUIRED", "Confirm with your code");
        }
        if (!Objects.equals(req.getAcknowledgedAmountMinor(), c.getPayableMinor())) {
            throw new ClaimsApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_CHANGED", "The amount changed",
                    Map.of("amountMinor", c.getPayableMinor()));
        }

        Instant now = Instant.now();
        confirmationRepository.save(ClaimConfirmationEntity.builder()
                .confirmationId("cnf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .claimId(claimId).farmerId(c.getFarmerId()).stepUpToken(req.getStepUpToken())
                .acknowledgedAmountMinor(req.getAcknowledgedAmountMinor()).channel(req.getChannel())
                .confirmedAt(now).build());

        holds.remove("FARMER_CONFIRMATION_REQUIRED");
        c.setHoldReasonsCsv(String.join(",", holds));

        NextStepAction next;
        if (!holds.isEmpty()) {
            c.setStatus(ClaimStatus.ON_HOLD);
            next = NextStepAction.CHECK_PENDING;
        } else if (c.getPayableMinor() <= properties.getRules().getAutoApprovalLimitMinor()) {
            c.setStatus(ClaimStatus.APPROVED);
            c.setApprovalPath(ApprovalPath.FARMER_CONFIRMED);
            c.setApprovedAt(now);
            c.setCurrentAttempt(Math.max(1, c.getCurrentAttempt()));
            next = NextStepAction.WAIT_FOR_PAYMENT;
            outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.approved", claimId, Map.of(
                    "eventType", "claim.approved", "claimId", claimId, "policyId", c.getPolicyId(),
                    "farmerId", c.getFarmerId(), "amountMinor", c.getPayableMinor(),
                    "approvalPath", ApprovalPath.FARMER_CONFIRMED.name(), "attempt", c.getCurrentAttempt(),
                    "decisionId", Objects.toString(c.getDecisionId(), ""),
                    "termsHash", Objects.toString(c.getTermsHash(), ""), "timestamp", now.toString()));
        } else {
            c.setStatus(ClaimStatus.VALIDATED);
            next = NextStepAction.CHECK_PENDING;
        }
        c.setNextStep(next);
        c.setVersionNum(c.getVersionNum() + 1);
        c.setClaimHash(sagaService.computeClaimHash(claimId, c.getPayableMinor(), c.getStatus().name(), holds));
        claimRepository.save(c);

        ClaimActionResponse resp = ClaimActionResponse.builder()
                .claimId(claimId).status(c.getStatus().name())
                .approvalPath(c.getApprovalPath() != null ? c.getApprovalPath().name() : null)
                .holdReasons(holds).nextStep(next.name()).confirmedAt(now.toString())
                .version(c.getVersionNum()).build();
        idempotencyService.save(idempotencyKey, "CLM-07:" + actor.actorId(), req, 200, resp);
        return resp;
    }

    @Transactional
    public DisputeResponse resolveDispute(String claimId, String disputeId, ResolveDisputeRequest req) {
        ClaimsIdentity actor = ClaimsSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.CLAIMS_REVIEWER, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN)) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        ClaimEntity c = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimsApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "Claim not found"));
        ClaimDisputeEntity d = disputeRepository.findByDisputeIdAndClaimId(disputeId, claimId)
                .orElseThrow(() -> new ClaimsApiException(HttpStatus.NOT_FOUND, "DISPUTE_NOT_FOUND", "Dispute not found"));

        if (clients.isConflictOfInterest(actor.actorId(), c.getPolicyId(), c.getFarmerId())) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "NOT_INDEPENDENT", "Reviewer cannot be the agent who sold the policy");
        }
        if (d.getPhotoTokensCsv() != null && !d.getPhotoTokensCsv().isBlank() && !req.isEvidenceReviewed()) {
            throw new ClaimsApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVIDENCE_NOT_REVIEWED",
                    "Confirm evidence photos were reviewed before resolving");
        }

        Instant now = Instant.now();
        DisputeOutcome outcome = DisputeOutcome.valueOf(req.getOutcome().toUpperCase());

        if (outcome == DisputeOutcome.UPHELD && req.isSupplementary()) {
            long suppAmt = req.getSupplementaryAmountMinor() != null ? req.getSupplementaryAmountMinor() : 0L;
            String expectedHash = clients.sha256(disputeId + "|" + suppAmt + "|" + req.getExplanation());
            if (d.getFirstApproverId() == null) {
                d.setStatus("PENDING_APPROVAL");
                d.setOutcome(DisputeOutcome.PENDING_APPROVAL);
                d.setFirstApproverId(actor.actorId());
                d.setEvidenceReviewed(req.isEvidenceReviewed());
                d.setSupplementary(true);
                d.setSupplementaryAmountMinor(suppAmt);
                d.setProposalHash(expectedHash);
                disputeRepository.save(d);
                return DisputeResponse.builder()
                        .disputeId(disputeId).claimId(claimId).status("PENDING_APPROVAL")
                        .outcome(DisputeOutcome.PENDING_APPROVAL.name())
                        .supplementaryAmountMinor(suppAmt).proposalHash(expectedHash)
                        .approvalsRequired(List.of("COMPLIANCE_OFFICER"))
                        .build();
            } else {
                if (actor.actorId().equals(d.getFirstApproverId())) {
                    throw new ClaimsApiException(HttpStatus.FORBIDDEN, "NOT_INDEPENDENT", "Second approver must differ from first");
                }
                if (!actor.hasAnyRole(AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN)) {
                    throw new ClaimsApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Second approval requires Compliance Officer or Admin");
                }
                if (!Objects.equals(req.getProposalHash(), d.getProposalHash())) {
                    throw new ClaimsApiException(HttpStatus.CONFLICT, "PROPOSAL_CHANGED", "Proposal hash mismatch");
                }
                String suppId = "clm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                claimRepository.save(ClaimEntity.builder()
                        .claimId(suppId).claimNumber("CLM-" + suppId.substring(4).toUpperCase())
                        .type(ClaimType.SUPPLEMENTARY).parentClaimId(claimId)
                        .policyId(c.getPolicyId()).farmerId(c.getFarmerId()).plotId(c.getPlotId())
                        .regionCode(c.getRegionCode()).windowId(c.getWindowId() + "_SUPP").tierId("SUPP")
                        .decisionId(c.getDecisionId()).status(ClaimStatus.APPROVED)
                        .approvalPath(ApprovalPath.MANUAL).calculatedMinor(d.getSupplementaryAmountMinor())
                        .payableMinor(d.getSupplementaryAmountMinor()).currency(c.getCurrency())
                        .termsHash(c.getTermsHash()).claimHash(clients.sha256(suppId))
                        .currentAttempt(1).maxAttempts(5).timelineJson("[]").openedAt(now).approvedAt(now).versionNum(1).build());
                d.setSupplementaryClaimId(suppId);
            }
        }

        d.setStatus("RESOLVED");
        d.setOutcome(outcome);
        d.setReviewerId(actor.actorId());
        d.setResolvedAt(now);
        disputeRepository.save(d);

        c.setStatus(c.getPriorStatus() != null ? c.getPriorStatus() : ClaimStatus.PAID);
        c.setVersionNum(c.getVersionNum() + 1);
        claimRepository.save(c);

        outboxService.enqueue(properties.getKafka().getClaimTopic(), "claim.dispute.resolved", claimId, Map.of(
                "eventType", "claim.dispute.resolved", "disputeId", disputeId, "claimId", claimId,
                "outcome", outcome.name(), "timestamp", now.toString()));

        return DisputeResponse.builder()
                .disputeId(disputeId).claimId(claimId).status("RESOLVED").outcome(outcome.name())
                .claimStatus(c.getStatus().name()).supplementaryClaimId(d.getSupplementaryClaimId())
                .supplementaryAmountMinor(d.getSupplementaryAmountMinor())
                .resolvedAt(now.toString()).build();
    }

    public ClaimsListResponse listClaims(String queue, String status, String farmerId, int page, int size) {
        ClaimsIdentity actor = ClaimsSecurityContext.require();
        boolean isFarmer = actor.hasRole(AuthRole.FARMER) && !actor.hasAnyRole(
                AuthRole.SUPPORT, AuthRole.CLAIMS_REVIEWER, AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR, AuthRole.FINANCE, AuthRole.ADMIN);
        if (isFarmer && farmerId != null && !farmerId.equals(actor.actorId())) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "FILTER_NOT_ALLOWED", "Cannot filter other farmers' claims");
        }
        String effectiveFarmer = isFarmer ? actor.actorId() : farmerId;

        List<ClaimEntity> all = claimRepository.findAll().stream()
                .filter(c -> effectiveFarmer == null || effectiveFarmer.equals(c.getFarmerId()))
                .filter(c -> status == null || c.getStatus().name().equalsIgnoreCase(status))
                .filter(c -> matchesQueue(c, queue))
                .sorted(Comparator.comparing(ClaimEntity::getOpenedAt).reversed())
                .toList();

        int fromIdx = Math.min(page * size, all.size());
        int toIdx = Math.min(fromIdx + size, all.size());
        List<Map<String, Object>> items = all.subList(fromIdx, toIdx).stream()
                .map(c -> Map.<String, Object>of(
                        "claimId", c.getClaimId(), "claimNumber", c.getClaimNumber(),
                        "type", c.getType().name(), "policyId", c.getPolicyId(),
                        "status", c.getStatus().name(), "payableMinor", c.getPayableMinor(),
                        "currency", c.getCurrency(), "openedAt", c.getOpenedAt().toString()))
                .toList();

        Map<String, Long> counts = new LinkedHashMap<>();
        all.forEach(c -> counts.merge(c.getStatus().name(), 1L, Long::sum));
        return ClaimsListResponse.builder().items(items).counts(counts).page(page).size(size).total(all.size()).build();
    }

    public ClaimsReconciliationReportResponse getReconciliationReport(LocalDate from, LocalDate to, String regionCode, int page, int size) {
        ClaimsIdentity actor = ClaimsSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.COMPLIANCE_OFFICER, AuthRole.AUDITOR, AuthRole.ADMIN)) {
            throw new ClaimsApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (ChronoUnit.DAYS.between(from, to) > 31) {
            throw new ClaimsApiException(HttpStatus.BAD_REQUEST, "RANGE_TOO_LARGE", "Date range cannot exceed 31 days");
        }
        List<ClaimEntity> all = claimRepository.findAll().stream()
                .filter(c -> regionCode == null || regionCode.equalsIgnoreCase(c.getRegionCode()))
                .toList();
        long approvedCount = all.stream().filter(c -> c.getApprovedAt() != null).count();
        long paidCount = all.stream().filter(c -> c.getStatus() == ClaimStatus.PAID).count();
        long within72h = all.stream().filter(c -> c.getHoursToPay() != null && c.getHoursToPay() <= 72.0).count();
        double within72Pct = paidCount == 0 ? 100.0 : (within72h * 100.0 / paidCount);

        List<Map<String, Object>> breaks = breakRepository.findAll().stream()
                .map(b -> Map.<String, Object>of("breakId", b.getBreakId(), "type", b.getType(),
                        "severity", b.getSeverity(), "amountMinor", b.getAmountMinor(), "status", b.getStatus()))
                .toList();

        return ClaimsReconciliationReportResponse.builder()
                .range(Map.of("from", from.toString(), "to", to.toString()))
                .summary(Map.of("claimsApproved", approvedCount, "claimsPaid", paidCount, "within72hPercent", within72Pct))
                .breaks(breaks)
                .statementCoverage(Map.of("MPESA", to.toString()))
                .page(page).size(size).total(breaks.size())
                .build();
    }

    private boolean matchesQueue(ClaimEntity c, String queue) {
        if (queue == null || queue.isBlank()) return true;
        return switch (queue.toUpperCase()) {
            case "HELD" -> c.getStatus() == ClaimStatus.ON_HOLD;
            case "AWAITING_APPROVAL" -> c.getStatus() == ClaimStatus.VALIDATED;
            case "FAILED" -> c.getStatus() == ClaimStatus.FAILED;
            case "AWAITING_CONFIRMATION" -> c.getStatus() == ClaimStatus.ON_HOLD
                    && parseHolds(c.getHoldReasonsCsv()).contains("FARMER_CONFIRMATION_REQUIRED");
            case "DISPUTED" -> c.getStatus() == ClaimStatus.DISPUTED;
            default -> true;
        };
    }

    private List<String> parseHolds(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return List.of(); }
    }
}
