package com.umar.farmer.api.grpc;

import com.umar.farmer.config.FarmerProperties;
import com.umar.farmer.domain.entity.CooperativeGroupEntity;
import com.umar.farmer.domain.entity.FarmerProfileEntity;
import com.umar.farmer.exception.FarmerApiException;
import com.umar.farmer.service.FarmerProfileService;
import com.umar.grpc.farmer.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FarmerGrpcServiceImpl extends FarmerServiceGrpc.FarmerServiceImplBase {

    private final FarmerProfileService profileService;
    private final FarmerProperties properties;

    @Override
    public void getPayoutDestination(
            GetPayoutDestinationRequest request,
            StreamObserver<GetPayoutDestinationResponse> responseObserver) {
        try {
            String caller = FarmerGrpcAuthInterceptor.CALLER_CTX_KEY.get();
            boolean allowedForPayout = properties.getSecurity().getPayoutDestinationConsumers().stream()
                    .anyMatch(c -> c.equalsIgnoreCase(caller));
            if (!allowedForPayout) {
                responseObserver.onError(Status.PERMISSION_DENIED
                        .withDescription("Only Payout and Policy services may read payout destination")
                        .asRuntimeException());
                return;
            }
            UUID farmerId = parseUuid(request.getFarmerId());
            Instant asOf = (request.getAsOf() != null && !request.getAsOf().isBlank())
                    ? Instant.parse(request.getAsOf())
                    : Instant.now();

            FarmerProfileService.PayoutDestinationSnapshot snap =
                    profileService.getPayoutDestination(farmerId, asOf, request.getCheckOnly());

            GetPayoutDestinationResponse.Builder b = GetPayoutDestinationResponse.newBuilder()
                    .setUsable(snap.usable())
                    .setHold(GrpcPayoutHold.valueOf(snap.hold()))
                    .setPendingChange(snap.pendingChange())
                    .setAsOf(snap.asOf());
            if (snap.walletId() != null) b.setWalletId(snap.walletId());
            if (snap.provider() != null) b.setProvider(snap.provider());
            if (snap.maskedNumber() != null) b.setMaskedNumber(snap.maskedNumber());
            if (snap.walletToken() != null) b.setWalletToken(snap.walletToken());
            if (snap.fallbackWalletId() != null) b.setFallbackWalletId(snap.fallbackWalletId());

            responseObserver.onNext(b.build());
            responseObserver.onCompleted();
        } catch (FarmerApiException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getFarmerContext(
            GetFarmerContextRequest request,
            StreamObserver<GetFarmerContextResponse> responseObserver) {
        try {
            UUID farmerId = parseUuid(request.getFarmerId());
            FarmerProfileService.FarmerContextSnapshot snap = profileService.getFarmerContext(farmerId);
            FarmerProfileEntity p = snap.profile();

            Set<GrpcFarmerContextField> fields = request.getFieldsList().isEmpty()
                    ? EnumSet.of(GrpcFarmerContextField.COMMUNICATION, GrpcFarmerContextField.REGION,
                            GrpcFarmerContextField.FARM, GrpcFarmerContextField.GROUP, GrpcFarmerContextField.STATUS)
                    : EnumSet.copyOf(request.getFieldsList());

            GetFarmerContextResponse.Builder b = GetFarmerContextResponse.newBuilder()
                    .setStatus(GrpcFarmerStatus.valueOf(p.getStatus().name()))
                    .setVersion(p.getVersion())
                    .setAsOf(snap.asOf());

            if (fields.contains(GrpcFarmerContextField.COMMUNICATION)) {
                CommunicationContext.Builder cb = CommunicationContext.newBuilder()
                        .setPreferredLanguage(p.getPreferredLanguage())
                        .setPreferredChannel(p.getPreferredChannel().name())
                        .setUrgentOverrideAllowed(true);
                if (p.getQuietStart() != null) {
                    cb.setQuietHours(QuietHoursMsg.newBuilder()
                            .setStart(p.getQuietStart())
                            .setEnd(p.getQuietEnd() != null ? p.getQuietEnd() : "")
                            .setTimezone(p.getQuietTimezone() != null ? p.getQuietTimezone() : "UTC")
                            .build());
                }
                b.setCommunication(cb.build());
            }
            if (fields.contains(GrpcFarmerContextField.REGION)) {
                b.setRegion(RegionContext.newBuilder()
                        .setCountry(p.getCountry())
                        .setDistrict(p.getDistrict())
                        .setWard(p.getWard())
                        .build());
            }
            if (fields.contains(GrpcFarmerContextField.FARM)) {
                b.setFarm(FarmContext.newBuilder()
                        .setFarmSizeBand(p.getFarmSizeBand().name())
                        .addAllPrimaryCrops(p.getPrimaryCrops())
                        .build());
            }
            if (fields.contains(GrpcFarmerContextField.GROUP) && snap.group() != null) {
                CooperativeGroupEntity g = snap.group();
                b.setGroup(GroupContext.newBuilder()
                        .setGroupId(g.getGroupId().toString())
                        .setGroupType(g.getGroupType().name())
                        .setSponsorOrgId(g.getSponsorOrgId() != null ? g.getSponsorOrgId() : "")
                        .build());
            }
            responseObserver.onNext(b.build());
            responseObserver.onCompleted();
        } catch (FarmerApiException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("farmer_id is required");
        return UUID.fromString(raw.trim());
    }
}
