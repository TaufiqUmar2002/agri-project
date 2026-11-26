package com.umar;

import com.umar.enums.riskpool.PoolStatus;
import com.umar.riskpool.domain.RiskPoolEntity;
import com.umar.riskpool.grpc.RiskPoolGrpcServer;
import com.umar.riskpool.grpc.proto.CheckCapacityRequest;
import com.umar.riskpool.grpc.proto.CheckCapacityResponse;
import com.umar.riskpool.grpc.proto.PoolServiceGrpc;
import com.umar.riskpool.repository.RiskPoolRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = RiskPoolServiceApplication.class)
@ActiveProfiles("test")
class RiskPoolGrpcTest {

    @Autowired private RiskPoolGrpcServer grpcServer;
    @Autowired private RiskPoolRepository poolRepository;

    private ManagedChannel channel;

    @AfterEach
    void tearDown() { if (channel != null) channel.shutdownNow(); }

    @Test
    void rpl12_checkCapacityAndReserveOnlyForPolicyService() {
        poolRepository.save(RiskPoolEntity.builder()
                .poolId("pool_g1").poolCode("KE_GRPC_1").nameJson("{\"en\":\"Pool\"}")
                .insurerRef("ins_kenya_mutual").countryCode("KE").currency("KES")
                .mappingsJson("[{\"productIds\":[\"prd_g1\"],\"regionCodes\":[\"KE-NYERI\"]}]")
                .accountingJson("{}").limitsJson("{\"totalExposureCapMinor\":5000000}")
                .alertsJson("[]").status(PoolStatus.ACTIVE).configHash("h1")
                .versionNum(1).alertVersion(1).authorId("fin-1")
                .effectiveFrom(LocalDate.now()).createdAt(Instant.now()).build());

        channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpcServer.getPort()).usePlaintext().build();

        Metadata badMd = new Metadata();
        badMd.put(Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER), "dev-internal-service-token");
        badMd.put(Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER), "claims-service");
        PoolServiceGrpc.PoolServiceBlockingStub badStub = PoolServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(badMd));
        assertThatThrownBy(() -> badStub.checkCapacity(CheckCapacityRequest.newBuilder()
                .setPolicyId("pol_g1").setProductId("prd_g1").setRegionCode("KE-NYERI")
                .setPeril("DROUGHT").setSeason("2026-LR").setSumInsuredMinor(100_000L).setCurrency("KES").build()))
                .isInstanceOf(StatusRuntimeException.class).hasMessageContaining("PERMISSION_DENIED");

        Metadata goodMd = new Metadata();
        goodMd.put(Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER), "dev-internal-service-token");
        goodMd.put(Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER), "policy-service");
        PoolServiceGrpc.PoolServiceBlockingStub goodStub = PoolServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(goodMd));

        CheckCapacityResponse resp = goodStub.checkCapacity(CheckCapacityRequest.newBuilder()
                .setPolicyId("pol_g1").setProductId("prd_g1").setRegionCode("KE-NYERI")
                .setPeril("DROUGHT").setSeason("2026-LR").setSumInsuredMinor(1_000_000L)
                .setCurrency("KES").setMode("RESERVE").build());
        assertThat(resp.getAllowed()).isTrue();
        assertThat(resp.getPoolId()).isEqualTo("pool_g1");
        assertThat(resp.getReservation().getReservationId()).startsWith("cap_");
    }
}
