package com.umar;

import com.umar.enums.policy.PayerType;
import com.umar.enums.policy.PayoutState;
import com.umar.enums.policy.PerilType;
import com.umar.enums.policy.PolicyStatus;
import com.umar.policy.api.grpc.PolicyGrpcServer;
import com.umar.policy.entity.PolicyEntity;
import com.umar.policy.grpc.*;
import com.umar.policy.repository.PolicyRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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

@SpringBootTest(classes = PolicyServiceApplication.class)
@ActiveProfiles("test")
class PolicyServiceGrpcTest {

    @Autowired private PolicyGrpcServer grpcServer;
    @Autowired private PolicyRepository policyRepository;

    @Test
    void pol10_pol11_getPolicyTermsAndListFarmerPolicies() {
        UUID farmerId = UUID.randomUUID();
        Instant now = Instant.now();
        policyRepository.save(PolicyEntity.builder()
                .policyId("pol_grpc_1")
                .policyNumber("AGS-KE-26-999001")
                .farmerId(farmerId)
                .plotId("pl_grpc_1")
                .quoteId("qt_grpc_1")
                .productId("prd_grpc")
                .productVersion(1)
                .peril(PerilType.DROUGHT)
                .definitionHash("defhash_1")
                .termsSnapshotJson("{}")
                .termsHash("termshash_1")
                .status(PolicyStatus.ACTIVE)
                .sumInsuredMinor(5_400_000L)
                .currency("KES")
                .totalPayableMinor(324_000L)
                .subsidyMinor(135_000L)
                .levyMinor(3_750L)
                .payerType(PayerType.FARMER)
                .premiumStatus("PAID")
                .paidAt(now.minus(10, ChronoUnit.DAYS))
                .waitingPeriodDays(7)
                .coverageStart(now.minus(3, ChronoUnit.DAYS))
                .coverageEnd(now.plus(87, ChronoUnit.DAYS))
                .payoutState(PayoutState.NONE)
                .paidToDateMinor(0L)
                .summaryText("Maize cover")
                .summaryLanguage("en")
                .termsVersion("5")
                .acceptanceJson("{}")
                .createdAt(now)
                .updatedAt(now)
                .build());

        ManagedChannel ch = ManagedChannelBuilder.forAddress("localhost", grpcServer.getPort())
                .usePlaintext()
                .build();
        try {
            PolicyServiceGrpc.PolicyServiceBlockingStub unauth = PolicyServiceGrpc.newBlockingStub(ch);
            assertThatThrownBy(() -> unauth.getPolicyTerms(GetPolicyTermsRequest.newBuilder()
                    .addPolicyIds("pol_grpc_1").build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);

            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER), "local-internal-service-token");
            headers.put(Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER), "trigger-engine");
            PolicyServiceGrpc.PolicyServiceBlockingStub stub = PolicyServiceGrpc.newBlockingStub(ch)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

            GetPolicyTermsResponse termsResp = stub.getPolicyTerms(GetPolicyTermsRequest.newBuilder()
                    .addPolicyIds("pol_grpc_1")
                    .addPolicyIds("pol_missing")
                    .build());
            assertThat(termsResp.getPoliciesCount()).isEqualTo(1);
            assertThat(termsResp.getPolicies(0).getTermsHash()).isEqualTo("termshash_1");
            assertThat(termsResp.getNotFoundIdsList()).containsExactly("pol_missing");

            ListFarmerPoliciesResponse listResp = stub.listFarmerPolicies(ListFarmerPoliciesRequest.newBuilder()
                    .setFarmerId(farmerId.toString())
                    .addStatuses("ACTIVE")
                    .build());
            assertThat(listResp.getPoliciesCount()).isEqualTo(1);
            assertThat(listResp.getPolicies(0).getPolicyNumber()).isEqualTo("AGS-KE-26-999001");
        } finally {
            ch.shutdownNow();
        }
    }
}
