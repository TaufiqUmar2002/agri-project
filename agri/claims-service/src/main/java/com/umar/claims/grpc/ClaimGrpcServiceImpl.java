package com.umar.claims.grpc;

import com.umar.claims.client.ClaimsExternalClients;
import com.umar.claims.config.ClaimsProperties;
import com.umar.claims.domain.ClaimEntity;
import com.umar.claims.grpc.proto.ClaimServiceGrpc;
import com.umar.claims.grpc.proto.GetPaymentInstructionRequest;
import com.umar.claims.grpc.proto.GetPaymentInstructionResponse;
import com.umar.claims.repository.ClaimRepository;
import com.umar.enums.claims.ClaimStatus;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClaimGrpcServiceImpl extends ClaimServiceGrpc.ClaimServiceImplBase {

    private final ClaimRepository claimRepository;
    private final ClaimsExternalClients clients;
    private final ClaimsProperties properties;

    @Override
    public void getPaymentInstruction(GetPaymentInstructionRequest req,
                                      StreamObserver<GetPaymentInstructionResponse> observer) {
        String caller = ClaimGrpcAuthInterceptor.CALLER_ID.get();
        List<String> allowed = Arrays.stream(properties.getGrpc().getAllowedCallers().split(","))
                .map(String::trim).map(String::toLowerCase).toList();
        if (caller == null || !allowed.contains(caller)) {
            observer.onError(Status.PERMISSION_DENIED.withDescription("Caller is not payout-service").asRuntimeException());
            return;
        }
        if (req.getClaimId().isBlank() || req.getAttempt() < 1 || req.getAttempt() > 5 || req.getPayoutId().isBlank()) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("Valid claim_id, attempt 1..5 and payout_id required").asRuntimeException());
            return;
        }
        ClaimEntity c = claimRepository.findById(req.getClaimId()).orElse(null);
        if (c == null) {
            observer.onError(Status.NOT_FOUND.withDescription("Claim not found").asRuntimeException());
            return;
        }

        Instant now = Instant.now();
        boolean payable = true;
        String reasonCode = "OK";
        boolean retryable = false;
        String holdReason = "";

        if (c.isCancelRequested()) {
            payable = false;
            reasonCode = "CANCEL_REQUESTED";
        } else if (!(c.getStatus() == ClaimStatus.APPROVED
                || (c.getStatus() == ClaimStatus.PAYING && req.getPayoutId().equals(c.getPayoutId())))) {
            payable = false;
            reasonCode = "CLAIM_NOT_APPROVED";
            holdReason = Objects.toString(c.getHoldReasonsCsv(), "");
        } else if (req.getAttempt() != c.getCurrentAttempt()) {
            payable = false;
            reasonCode = "ATTEMPT_MISMATCH";
        } else if (c.getHoldReasonsCsv() != null && !c.getHoldReasonsCsv().isBlank()) {
            payable = false;
            reasonCode = "ACTIVE_HOLD";
            holdReason = c.getHoldReasonsCsv();
        } else {
            if (!clients.isTriggerServiceAvailable() || !clients.isPolicyServiceAvailable()) {
                observer.onError(Status.UNAVAILABLE.withDescription("Upstream verification unavailable").asRuntimeException());
                return;
            }
            if (c.getDecisionId() != null && !clients.fetchTriggerStatus(c.getDecisionId()).finalBreach()) {
                payable = false;
                reasonCode = "DECISION_NOT_FINAL";
            } else if (!clients.fetchPolicyTerms(c.getPolicyId()).active()) {
                payable = false;
                reasonCode = "POLICY_INACTIVE";
            }
        }

        observer.onNext(GetPaymentInstructionResponse.newBuilder()
                .setPayable(payable)
                .setAmountMinor(payable ? c.getPayableMinor() : 0L)
                .setCurrency(c.getCurrency())
                .setRoute(Objects.toString(c.getPayoutRoute(), "SAME_WALLET"))
                .setUseCurrentDestination(c.isUseCurrentDestination())
                .setAsOf(now.toString())
                .setDecisionId(Objects.toString(c.getDecisionId(), ""))
                .setTermsHash(Objects.toString(c.getTermsHash(), ""))
                .setClaimVersion(c.getVersionNum())
                .setExpiresAt(now.plus(30, ChronoUnit.MINUTES).toString())
                .setReasonCode(reasonCode)
                .setRetryable(retryable)
                .setHoldReason(holdReason)
                .build());
        observer.onCompleted();
    }
}
