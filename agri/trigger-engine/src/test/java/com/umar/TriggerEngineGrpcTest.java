package com.umar;

import com.umar.enums.policy.PerilType;
import com.umar.enums.trigger.DataSourceQuality;
import com.umar.enums.trigger.TriggerDecisionOutcome;
import com.umar.enums.trigger.TriggerDecisionStatus;
import com.umar.trigger.domain.SimulationRunEntity;
import com.umar.trigger.domain.TriggerDecisionEntity;
import com.umar.trigger.grpc.TriggerGrpcServer;
import com.umar.trigger.grpc.proto.*;
import com.umar.trigger.repository.SimulationRunRepository;
import com.umar.trigger.repository.TriggerDecisionRepository;
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

@SpringBootTest(classes = TriggerEngineApplication.class)
@ActiveProfiles("test")
class TriggerEngineGrpcTest {

    @Autowired private TriggerGrpcServer grpcServer;
    @Autowired private TriggerDecisionRepository decisionRepository;
    @Autowired private SimulationRunRepository simulationRepository;

    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) channel.shutdownNow();
    }

    @Test
    void trg09_trg10_decisionStatusAndSimulationSummaryVerification() {
        decisionRepository.save(TriggerDecisionEntity.builder()
                .decisionId("dec_grpc_1")
                .evaluationId("ev_g1")
                .policyId("pol_g1")
                .farmerId("frm_g1")
                .plotId("plt_g1")
                .regionCode("KE-NYERI")
                .ruleId("rul_g1")
                .ruleVersion(1)
                .ruleHash("rh_1")
                .windowId("2026-10")
                .windowStart(LocalDate.of(2026, 10, 1))
                .windowEnd(LocalDate.of(2026, 10, 31))
                .outcome(TriggerDecisionOutcome.BREACH)
                .status(TriggerDecisionStatus.FINAL)
                .tierId("T2")
                .payoutPercent(60.0)
                .indicatedPayoutMinor(600_000L)
                .currency("KES")
                .computedIndexValue(42.0)
                .thresholdValue(50.0)
                .dataQuality(DataSourceQuality.GOOD)
                .evidenceHash("eh_1")
                .evidenceBundleToken("vt_ev_1")
                .evidenceJson("[]")
                .phasesJson("[]")
                .corroborationJson("{}")
                .historyJson("[]")
                .largeEvent(false)
                .decidedAt(Instant.now())
                .versionNum(1)
                .build());

        simulationRepository.save(SimulationRunEntity.builder()
                .simulationId("sim_grpc_1")
                .ruleId("rul_g1")
                .ruleVersion(1)
                .definitionHash("def_hash_abc")
                .regionCodesCsv("KE-NYERI")
                .fromDate(LocalDate.of(2016, 1, 1))
                .toDate(LocalDate.of(2026, 1, 1))
                .seasonsCovered(12)
                .expectedLossRatio(0.62)
                .burningCostPercent(6.2)
                .dataCoveragePercent(98.4)
                .summaryJson("{}")
                .perSeasonJson("[]")
                .validUntil(LocalDate.now().plusDays(30))
                .requestedBy("uw-1")
                .createdAt(Instant.now())
                .build());

        channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpcServer.getPort()).usePlaintext().build();

        // Claims Service calls TRG-09
        Metadata claimsMd = new Metadata();
        claimsMd.put(Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER), "dev-internal-service-token");
        claimsMd.put(Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER), "claims-service");
        TriggerServiceGrpc.TriggerServiceBlockingStub claimsStub = TriggerServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(claimsMd));

        GetDecisionStatusResponse res = claimsStub.getDecisionStatus(GetDecisionStatusRequest.newBuilder()
                .addDecisionIds("dec_grpc_1")
                .addDecisionIds("dec_missing")
                .setRequestId("req-1")
                .build());
        assertThat(res.getDecisionsCount()).isEqualTo(1);
        assertThat(res.getDecisions(0).getOutcome()).isEqualTo("BREACH");
        assertThat(res.getNotFoundIdsList()).containsExactly("dec_missing");

        // Claims Service cannot call TRG-10 -> PERMISSION_DENIED
        assertThatThrownBy(() -> claimsStub.getSimulationSummary(GetSimulationSummaryRequest.newBuilder()
                .setSimulationId("sim_grpc_1").setDefinitionHash("def_hash_abc").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("PERMISSION_DENIED");

        // Policy Service calls TRG-10
        Metadata policyMd = new Metadata();
        policyMd.put(Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER), "dev-internal-service-token");
        policyMd.put(Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER), "policy-service");
        TriggerServiceGrpc.TriggerServiceBlockingStub policyStub = TriggerServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(policyMd));

        GetSimulationSummaryResponse simResp = policyStub.getSimulationSummary(GetSimulationSummaryRequest.newBuilder()
                .setSimulationId("sim_grpc_1")
                .setDefinitionHash("def_hash_abc")
                .build());
        assertThat(simResp.getValid()).isTrue();
        assertThat(simResp.getSeasonsCovered()).isEqualTo(12);
    }
}
