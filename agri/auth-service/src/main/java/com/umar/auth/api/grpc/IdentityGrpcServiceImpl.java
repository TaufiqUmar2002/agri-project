package com.umar.auth.api.grpc;

import com.umar.auth.exception.AuthApiException;
import com.umar.auth.service.DelegationService;
import com.umar.auth.service.IdentityContextService;
import com.umar.enums.auth.DelegationScope;
import com.umar.grpc.auth.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityGrpcServiceImpl extends IdentityServiceGrpc.IdentityServiceImplBase {

    private final IdentityContextService identityContextService;
    private final DelegationService delegationService;

    @Override
    public void getIdentityContext(
            GetIdentityContextRequest request,
            StreamObserver<GetIdentityContextResponse> responseObserver) {
        try {
            UUID userId = parseUuid(request.getUserId(), "user_id");
            boolean bypassCache = false;
            IdentityContextService.IdentityContextSnapshot snap =
                    identityContextService.getIdentityContext(userId, request.getPurposesList(), bypassCache);

            GetIdentityContextResponse.Builder builder = GetIdentityContextResponse.newBuilder()
                    .setAccountStatus(GrpcAccountStatus.valueOf(snap.accountStatus()))
                    .addAllRoles(snap.roles())
                    .setKycTier(GrpcKycTier.valueOf(snap.kycTier()))
                    .setLockedUntil(snap.lockedUntil() != null ? snap.lockedUntil() : "")
                    .setAsOf(snap.asOf());

            for (IdentityContextService.ConsentSnapshot c : snap.consents()) {
                GrpcConsentState state = switch (c.status()) {
                    case "ACTIVE" -> GrpcConsentState.CONSENT_ACTIVE;
                    case "WITHDRAWN" -> GrpcConsentState.CONSENT_WITHDRAWN;
                    default -> GrpcConsentState.CONSENT_MISSING;
                };
                builder.addConsents(ConsentContextItem.newBuilder()
                        .setPurpose(c.purpose())
                        .setStatus(state)
                        .setTextVersion(c.textVersion())
                        .setGrantedAt(c.grantedAt())
                        .build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (AuthApiException ex) {
            Status status = switch (ex.getStatus()) {
                case NOT_FOUND -> Status.NOT_FOUND;
                case BAD_REQUEST -> Status.INVALID_ARGUMENT;
                case FORBIDDEN -> Status.PERMISSION_DENIED;
                default -> Status.INTERNAL;
            };
            responseObserver.onError(status.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void validateDelegation(
            ValidateDelegationRequest request,
            StreamObserver<ValidateDelegationResponse> responseObserver) {
        try {
            UUID delegationId = parseUuid(request.getDelegationId(), "delegation_id");
            UUID agentId = parseUuid(request.getAgentId(), "agent_id");
            UUID farmerId = parseUuid(request.getFarmerUserId(), "farmer_user_id");

            if (request.getRequiredScope() == GrpcDelegationScope.DELEGATION_SCOPE_UNSPECIFIED
                    || request.getRequiredScope() == GrpcDelegationScope.UNRECOGNIZED) {
                throw new IllegalArgumentException("required_scope is required");
            }
            DelegationScope scope = DelegationScope.valueOf(request.getRequiredScope().name());
            DelegationService.ValidationResult res =
                    delegationService.validateDelegation(delegationId, agentId, farmerId, scope);

            ValidateDelegationResponse.Builder builder = ValidateDelegationResponse.newBuilder()
                    .setValid(res.valid())
                    .setReason(GrpcDelegationReason.valueOf(res.reason().name()))
                    .setValidUntil(res.validUntil() != null ? res.validUntil().toString() : "");
            for (DelegationScope s : res.grantedScopes()) {
                builder.addGrantedScopes(GrpcDelegationScope.valueOf(s.name()));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    private UUID parseUuid(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a valid UUID");
        }
    }
}
