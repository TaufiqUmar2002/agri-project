package com.umar;

import com.umar.enums.farmer.FarmSizeBand;
import com.umar.enums.farmer.FarmerStatus;
import com.umar.enums.farmer.PreferredChannel;
import com.umar.enums.farmer.WalletStatus;
import com.umar.farmer.api.grpc.FarmerGrpcServer;
import com.umar.farmer.domain.entity.FarmerProfileEntity;
import com.umar.farmer.domain.entity.FarmerWalletEntity;
import com.umar.farmer.repository.FarmerProfileRepository;
import com.umar.farmer.repository.FarmerWalletRepository;
import com.umar.grpc.farmer.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = FarmerProfileApplication.class)
@ActiveProfiles("test")
class FarmerProfileGrpcTest {

    @Autowired private FarmerGrpcServer grpcServer;
    @Autowired private FarmerProfileRepository profileRepository;
    @Autowired private FarmerWalletRepository walletRepository;

    private FarmerServiceGrpc.FarmerServiceBlockingStub stub(ManagedChannel ch, String caller) {
        Metadata headers = new Metadata();
        if (caller != null) headers.put(Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER), caller);
        return FarmerServiceGrpc.newBlockingStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @Test
    void far09_and_far10_coolingOffDestinationAndContext() {
        UUID farmerId = UUID.randomUUID();
        Instant now = Instant.now();
        profileRepository.save(FarmerProfileEntity.builder()
                .farmerId(farmerId)
                .userId(UUID.randomUUID())
                .status(FarmerStatus.ACTIVE)
                .legalNameToken("vt_name_1")
                .preferredLanguage("sw")
                .preferredChannel(PreferredChannel.WHATSAPP)
                .country("KE")
                .district("NYERI")
                .ward("MWEIGA")
                .farmSizeBand(FarmSizeBand.BAND_1_TO_2)
                .primaryCropsCsv("MAIZE")
                .fraudHold(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        UUID oldWalletId = UUID.randomUUID();
        walletRepository.save(FarmerWalletEntity.builder()
                .walletId(oldWalletId)
                .farmerId(farmerId)
                .provider("MPESA")
                .walletNumberToken("vt_wal_old")
                .walletNumberHash("h_old")
                .maskedNumber("+2547*****111")
                .status(WalletStatus.VERIFIED)
                .primary(false)
                .effectiveFrom(now.minus(30, ChronoUnit.DAYS))
                .createdAt(now.minus(30, ChronoUnit.DAYS))
                .build());

        UUID newWalletId = UUID.randomUUID();
        walletRepository.save(FarmerWalletEntity.builder()
                .walletId(newWalletId)
                .farmerId(farmerId)
                .provider("MPESA")
                .walletNumberToken("vt_wal_new")
                .walletNumberHash("h_new")
                .maskedNumber("+2547*****222")
                .status(WalletStatus.VERIFIED)
                .primary(true)
                .effectiveFrom(now.plus(20, ChronoUnit.HOURS))
                .createdAt(now)
                .build());

        ManagedChannel ch = NettyChannelBuilder.forAddress("localhost", grpcServer.getBoundPort()).usePlaintext().build();
        try {
            // Notification cannot call GetPayoutDestination
            assertThatThrownBy(() -> stub(ch, "notification-service").getPayoutDestination(
                    GetPayoutDestinationRequest.newBuilder().setFarmerId(farmerId.toString()).build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.PERMISSION_DENIED);

            // During cooling-off (now), old wallet is returned with pending_change = true
            GetPayoutDestinationResponse destNow = stub(ch, "payout-service").getPayoutDestination(
                    GetPayoutDestinationRequest.newBuilder()
                            .setFarmerId(farmerId.toString())
                            .setAsOf(now.toString())
                            .build());
            assertThat(destNow.getUsable()).isTrue();
            assertThat(destNow.getWalletId()).isEqualTo(oldWalletId.toString());
            assertThat(destNow.getPendingChange()).isTrue();

            // Notification can read GetFarmerContext
            GetFarmerContextResponse ctx = stub(ch, "notification-service").getFarmerContext(
                    GetFarmerContextRequest.newBuilder()
                            .setFarmerId(farmerId.toString())
                            .addFields(GrpcFarmerContextField.COMMUNICATION)
                            .build());
            assertThat(ctx.getCommunication().getPreferredLanguage()).isEqualTo("sw");
        } finally {
            ch.shutdownNow();
        }
    }
}
