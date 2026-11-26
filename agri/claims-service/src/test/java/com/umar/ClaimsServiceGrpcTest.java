package com.umar;

import com.umar.claims.client.ClaimsExternalClients;
import com.umar.claims.domain.ClaimEntity;
import com.umar.claims.grpc.ClaimGrpcServer;
import com.umar.claims.grpc.proto.ClaimServiceGrpc;
import com.umar.claims.grpc.proto.GetPaymentInstructionRequest;
import com.umar.claims.grpc.proto.GetPaymentInstructionResponse;
import com.umar.claims.service.ClaimsSagaService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ClaimsServiceApplication.class)
@ActiveProfiles("test")
class ClaimsServiceGrpcTest {

    @Autowired private ClaimGrpcServer grpcServer;
    @Autowired private ClaimsSagaService sagaService;
    @Autowired private ClaimsExternalClients clients;

    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) channel.shutdownNow();
    }

    @Test
    void clm10_getPaymentInstructionEnforcesCallerAndValidatesState() {
        clients.registerPolicyTerms("pol_cg1", new ClaimsExternalClients.PolicyTermsView(
                true, 100_000L, 0L, clients.sha256("pol_cg1"), "KES", "frm_cg1", "plt_cg1", "KE-NYERI"));
        clients.registerTriggerDecision("dec_cg1", new ClaimsExternalClients.TriggerStatusView(true, 50.0, "T1"));
        ClaimEntity clm = sagaService.onThresholdBreached(
                "evt_cg1", "dec_cg1", "pol_cg1", "rul_1", 1, "W_G1", "T1", 50.0, Instant.now());

        channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpcServer.getPort()).usePlaintext().build();

        // Wrong caller -> PERMISSION_DENIED
        Metadata badMd = new Metadata();
        badMd.put(Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER), "dev-internal-service-token");
        badMd.put(Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER), "notification-service");
        ClaimServiceGrpc.ClaimServiceBlockingStub badStub = ClaimServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(badMd));

        assertThatThrownBy(() -> badStub.getPaymentInstruction(GetPaymentInstructionRequest.newBuilder()
                .setClaimId(clm.getClaimId()).setAttempt(1).setPayoutId("pay_g1").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("PERMISSION_DENIED");

        // payout-service allowed -> payable = true
        Metadata goodMd = new Metadata();
        goodMd.put(Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER), "dev-internal-service-token");
        goodMd.put(Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER), "payout-service");
        ClaimServiceGrpc.ClaimServiceBlockingStub goodStub = ClaimServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(goodMd));

        GetPaymentInstructionResponse resp = goodStub.getPaymentInstruction(GetPaymentInstructionRequest.newBuilder()
                .setClaimId(clm.getClaimId()).setAttempt(1).setPayoutId("pay_g1").build());
        assertThat(resp.getPayable()).isTrue();
        assertThat(resp.getAmountMinor()).isEqualTo(50_000L);
        assertThat(resp.getCurrency()).isEqualTo("KES");
    }
}
