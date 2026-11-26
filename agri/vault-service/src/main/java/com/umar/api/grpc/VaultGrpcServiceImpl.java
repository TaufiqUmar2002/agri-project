package com.umar.api.grpc;

import com.google.protobuf.ByteString;
import com.umar.cryptography.HashService;
import com.umar.enums.vault.TokenFormat;
import com.umar.exception.PolicyViolationException;
import com.umar.exception.VaultApiException;
import com.umar.exception.VaultPolicyDeniedException;
import com.umar.grpc.vault.v1.*;
import com.umar.payload.request.vault.DeTokenizeRequest;
import com.umar.payload.request.vault.HashRequest;
import com.umar.payload.request.vault.SignRequest;
import com.umar.payload.request.vault.TokenizeRequest;
import com.umar.payload.response.vault.DetokenizeResponse;
import com.umar.payload.response.vault.HashResponse;
import com.umar.payload.response.vault.SignResponse;
import com.umar.payload.response.vault.TokenizeResponse;
import com.umar.policy.PolicyResource;
import com.umar.policy.VaultPolicyService;
import com.umar.service.IVaultService;
import com.umar.service.SecretService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * VLT-11 — VaultService gRPC implementation: Tokenize, Detokenize, Hash, Sign (+ streaming).
 */
@Service
@RequiredArgsConstructor
public class VaultGrpcServiceImpl extends VaultServiceGrpc.VaultServiceImplBase {

    private final IVaultService vaultService;
    private final HashService hashService;
    private final SecretService secretService;
    private final VaultPolicyService vaultPolicyService;

    @Override
    public void tokenize(TokenizeGrpcRequest request, StreamObserver<TokenizeGrpcResponse> responseObserver) {
        try {
            TokenizeGrpcResponse response = executeTokenize(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(mapException(t));
        }
    }

    @Override
    public void detokenize(DetokenizeGrpcRequest request, StreamObserver<DetokenizeGrpcResponse> responseObserver) {
        try {
            DetokenizeGrpcResponse response = executeDetokenize(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(mapException(t));
        }
    }

    @Override
    public void hash(HashGrpcRequest request, StreamObserver<HashGrpcResponse> responseObserver) {
        try {
            HashRequest restReq = HashRequest.builder()
                    .dataClass(request.getDataClass())
                    .purpose(request.getPurpose())
                    .contextRef(request.getContextRef())
                    .values(request.getValuesList())
                    .build();
            HashResponse res = hashService.computeHashes(restReq);

            HashGrpcResponse.Builder builder = HashGrpcResponse.newBuilder()
                    .setRequestId(res.getRequestId() != null ? res.getRequestId() : "");
            for (HashResponse.ValueHashResult vh : res.getHashes()) {
                ValueHashResult.Builder vhb = ValueHashResult.newBuilder().setValueIndex(vh.getValueIndex());
                for (HashResponse.HashEntry entry : vh.getEntries()) {
                    byte[] raw = java.util.HexFormat.of().parseHex(entry.getHash());
                    vhb.addEntries(HashVersionEntry.newBuilder()
                            .setHash(ByteString.copyFrom(raw))
                            .setKeyVersion(entry.getKeyVersion())
                            .setHashHex(entry.getHash())
                            .build());
                }
                builder.addHashes(vhb.build());
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(mapException(t));
        }
    }

    @Override
    public void sign(SignGrpcRequest request, StreamObserver<SignGrpcResponse> responseObserver) {
        try {
            vaultPolicyService.authorize("SIGN", List.of(new PolicyResource("SECRET", "GENERAL")));
            SignRequest restReq = SignRequest.builder()
                    .secretRef(request.getSecretRef())
                    .algorithm(request.getAlgorithm().isBlank() ? null : request.getAlgorithm())
                    .purpose(request.getPurpose())
                    .contextRef(request.getContextRef())
                    .payload(Base64.getEncoder().encodeToString(request.getPayload().toByteArray()))
                    .version(request.getVersion() > 0 ? request.getVersion() : null)
                    .build();
            SignResponse res = secretService.signPayload(restReq);
            byte[] sigBytes = Base64.getDecoder().decode(res.getSignature());

            responseObserver.onNext(SignGrpcResponse.newBuilder()
                    .setSignature(ByteString.copyFrom(sigBytes))
                    .setKeyVersion(res.getKeyVersion())
                    .setAlgorithm(res.getAlgorithm() != null ? res.getAlgorithm() : "")
                    .setKid(res.getKid() != null ? res.getKid() : "")
                    .setRequestId(res.getRequestId() != null ? res.getRequestId() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(mapException(t));
        }
    }

    @Override
    public StreamObserver<TokenizeGrpcRequest> tokenizeStream(StreamObserver<TokenizeGrpcResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(TokenizeGrpcRequest value) {
                try {
                    responseObserver.onNext(executeTokenize(value));
                } catch (Throwable t) {
                    responseObserver.onError(mapException(t));
                }
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<DetokenizeGrpcRequest> detokenizeStream(StreamObserver<DetokenizeGrpcResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(DetokenizeGrpcRequest value) {
                try {
                    responseObserver.onNext(executeDetokenize(value));
                } catch (Throwable t) {
                    responseObserver.onError(mapException(t));
                }
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    private TokenizeGrpcResponse executeTokenize(TokenizeGrpcRequest request) {
        List<TokenizeRequest.Items> items = request.getItemsList().stream()
                .map(i -> TokenizeRequest.Items.builder()
                        .dataClass(i.getDataClass())
                        .value(i.getValue().toCharArray())
                        .subjectRef(i.getSubjectRef())
                        .format("FPE".equalsIgnoreCase(i.getFormat()) ? TokenFormat.FPE : TokenFormat.OPAQUE)
                        .reuseExisting(i.getReuseExistingSet() ? i.getReuseExisting() : Boolean.TRUE)
                        .build())
                .toList();

        List<PolicyResource> resources = items.stream()
                .map(i -> new PolicyResource(i.getDataClass().toUpperCase(), request.getPurpose()))
                .distinct()
                .toList();
        if (!resources.isEmpty()) {
            vaultPolicyService.authorize("TOKENIZE", resources);
        }

        TokenizeRequest restReq = TokenizeRequest.builder()
                .purpose(request.getPurpose())
                .contextRef(request.getContextRef())
                .items(items)
                .build();
        String idemp = request.getRequestId().isBlank() ? null : request.getRequestId();
        TokenizeResponse res = vaultService.processTokenization(idemp, restReq);

        TokenizeGrpcResponse.Builder builder = TokenizeGrpcResponse.newBuilder()
                .setRequestId(res.getRequestId() != null ? res.getRequestId() : "");
        res.getTokens().forEach(t -> builder.addTokens(TokenGrpcSuccess.newBuilder()
                .setIndex(t.index())
                .setToken(t.token())
                .setDataClass(t.dataClass())
                .setMasked(t.masked() != null ? t.masked() : "")
                .setReused(t.reused())
                .setFormat(t.format() != null ? t.format() : "OPAQUE")
                .setCreatedAt(t.createdAt() != null ? t.createdAt().toString() : "")
                .build()));
        res.getFailures().forEach(f -> builder.addFailures(ItemGrpcFailure.newBuilder()
                .setIndex(f.index())
                .setCode(f.code())
                .setMessage(f.message())
                .build()));
        return builder.build();
    }

    private DetokenizeGrpcResponse executeDetokenize(DetokenizeGrpcRequest request) {
        DeTokenizeRequest restReq = DeTokenizeRequest.builder()
                .purpose(request.getPurpose())
                .contextRef(request.getContextRef())
                .tokens(request.getTokensList())
                .returnMode(request.getReturnMode().isBlank() ? "VALUE" : request.getReturnMode())
                .build();
        DetokenizeResponse res = vaultService.processDeTokenization(
                request.getRequestId().isBlank() ? null : request.getRequestId(), restReq);

        DetokenizeGrpcResponse.Builder builder = DetokenizeGrpcResponse.newBuilder()
                .setRequestId(res.getRequestId() != null ? res.getRequestId() : "");
        res.getValues().forEach(v -> builder.addValues(ResolvedGrpcValue.newBuilder()
                .setToken(v.getToken())
                .setDataClass(v.getDataClass())
                .setValue(v.getValue() != null ? v.getValue() : "")
                .build()));
        res.getFailures().forEach(f -> builder.addFailures(TokenGrpcFailure.newBuilder()
                .setToken(f.getToken())
                .setCode(f.getCode())
                .build()));
        return builder.build();
    }

    private Throwable mapException(Throwable t) {
        if (t instanceof VaultPolicyDeniedException || t instanceof PolicyViolationException) {
            return Status.PERMISSION_DENIED.withDescription(t.getMessage()).asRuntimeException();
        }
        if (t instanceof VaultApiException vae) {
            Status status = switch (vae.getStatus()) {
                case UNAUTHORIZED -> Status.UNAUTHENTICATED;
                case FORBIDDEN -> Status.PERMISSION_DENIED;
                case NOT_FOUND -> Status.NOT_FOUND;
                case TOO_MANY_REQUESTS -> Status.RESOURCE_EXHAUSTED;
                case SERVICE_UNAVAILABLE -> Status.UNAVAILABLE;
                case BAD_REQUEST, PAYLOAD_TOO_LARGE -> Status.INVALID_ARGUMENT;
                case UNPROCESSABLE_ENTITY, CONFLICT, GONE -> Status.FAILED_PRECONDITION;
                default -> Status.INTERNAL;
            };
            return status.withDescription(vae.getCode() + ": " + vae.getMessage()).asRuntimeException();
        }
        if (t instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(t.getMessage()).asRuntimeException();
        }
        return Status.INTERNAL.withDescription(t.getMessage()).asRuntimeException();
    }
}
