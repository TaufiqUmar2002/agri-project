package com.umar;

import com.google.protobuf.ByteString;
import com.umar.grpc.hub.v1.*;
import com.umar.integration.api.grpc.HubGrpcServer;
import com.umar.integration.domain.entity.ExtensionEnablementEntity;
import com.umar.integration.domain.entity.ExtensionVersionEntity;
import com.umar.integration.domain.enums.ExtensionStatus;
import com.umar.enums.integration.FailMode;
import com.umar.enums.integration.RuntimeKind;
import com.umar.integration.repository.ExtensionEnablementRepository;
import com.umar.integration.repository.ExtensionVersionRepository;
import com.umar.integration.service.support.SandboxRuntime;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = IntegrationServiceApplication.class)
@ActiveProfiles("test")
class IntegrationHubGrpcTest {

    @Autowired
    private HubGrpcServer grpcServer;

    @Autowired
    private ExtensionVersionRepository extensionVersionRepository;

    @Autowired
    private ExtensionEnablementRepository enablementRepository;

    @Autowired
    private SandboxRuntime sandboxRuntime;

    private ExtensionServiceGrpc.ExtensionServiceBlockingStub stubForService(ManagedChannel channel, String serviceName) {
        Metadata headers = new Metadata();
        if (serviceName != null) {
            headers.put(Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER), serviceName);
        }
        return ExtensionServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @Test
    void int13_and_int14_registerPointsAndInvokeExtensionsWithGuardrails() {
        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", grpcServer.getBoundPort())
                .usePlaintext()
                .build();
        try {
            // Unauthenticated call -> UNAUTHENTICATED
            ExtensionServiceGrpc.ExtensionServiceBlockingStub unauthStub = stubForService(channel, null);
            assertThatThrownBy(() -> unauthStub.registerExtensionPoints(RegisterExtensionPointsRequest.newBuilder()
                    .setServiceName("policy-service")
                    .setServiceVersion("1.0.0")
                    .build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);

            // INT-13: Mismatched caller service -> PERMISSION_DENIED
            ExtensionServiceGrpc.ExtensionServiceBlockingStub rogueStub = stubForService(channel, "rogue-service");
            assertThatThrownBy(() -> rogueStub.registerExtensionPoints(RegisterExtensionPointsRequest.newBuilder()
                    .setServiceName("policy-service")
                    .setServiceVersion("1.14.0")
                    .setSdkVersion("3.2.1")
                    .addPoints(DeclaredExtensionPoint.newBuilder()
                            .setHookId("policy.post-activate")
                            .setType("POST")
                            .setContractVersion("1.0")
                            .setDefaultTimeoutMs(1000)
                            .setMaxTimeoutMs(2000)
                            .addAllowedFailModes("FAIL_OPEN")
                            .setPartnerVisible(true)
                            .build())
                    .build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.PERMISSION_DENIED);

            // INT-13: Register valid new point for policy-service
            ExtensionServiceGrpc.ExtensionServiceBlockingStub policyStub = stubForService(channel, "policy-service");
            RegisterExtensionPointsResponse regResp = policyStub.registerExtensionPoints(
                    RegisterExtensionPointsRequest.newBuilder()
                            .setServiceName("policy-service")
                            .setServiceVersion("1.14.0")
                            .setSdkVersion("3.2.1")
                            .addPoints(DeclaredExtensionPoint.newBuilder()
                                    .setHookId("policy.pre-quote")
                                    .setType("PRE")
                                    .setContractVersion("2.1")
                                    .addDataScopes("quote.basic")
                                    .setDefaultTimeoutMs(2000)
                                    .setMaxTimeoutMs(5000)
                                    .addAllowedFailModes("FAIL_OPEN")
                                    .addAllowedFailModes("HOLD_FOR_REVIEW")
                                    .setPartnerVisible(true)
                                    .build())
                            .addPoints(DeclaredExtensionPoint.newBuilder()
                                    .setHookId("policy.post-activate")
                                    .setType("POST")
                                    .setContractVersion("1.0")
                                    .setDefaultTimeoutMs(1000)
                                    .setMaxTimeoutMs(2000)
                                    .addAllowedFailModes("FAIL_OPEN")
                                    .setPartnerVisible(true)
                                    .build())
                            .build());
            assertThat(regResp.getAcceptedList()).containsExactlyInAnyOrder("policy.pre-quote", "policy.post-activate");
            assertThat(regResp.getPollAfterSeconds()).isEqualTo(30);

            // INT-13: Breaking contract change (2.1 -> 5.0) -> FAILED_PRECONDITION
            assertThatThrownBy(() -> policyStub.registerExtensionPoints(RegisterExtensionPointsRequest.newBuilder()
                    .setServiceName("policy-service")
                    .setServiceVersion("2.0.0")
                    .setSdkVersion("3.2.1")
                    .addPoints(DeclaredExtensionPoint.newBuilder()
                            .setHookId("policy.pre-quote")
                            .setType("PRE")
                            .setContractVersion("5.0")
                            .setDefaultTimeoutMs(2000)
                            .setMaxTimeoutMs(5000)
                            .addAllowedFailModes("FAIL_OPEN")
                            .build())
                    .build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.FAILED_PRECONDITION);

            // INT-14: NO_EXTENSION when nothing is enabled for tenant
            InvokeExtensionResponse noExt = policyStub.invokeExtension(InvokeExtensionRequest.newBuilder()
                    .setHookId("policy.pre-quote")
                    .setContractVersion("2.1")
                    .setCallerService("policy-service")
                    .setContext(InvocationContext.newBuilder().setTenantId("ten_empty").build())
                    .setSubjectRef("qt_001")
                    .setInput(ByteString.copyFromUtf8("{\"quote\":{\"premiumMinor\":324000}}"))
                    .setDeadlineMs(2000)
                    .build());
            assertThat(noExt.getStatus()).isEqualTo(InvokeStatus.NO_EXTENSION);

            // Enable an extension on policy.pre-quote and another VALIDATE extension on claim.validator that violates rules
            Instant now = Instant.now();
            extensionVersionRepository.save(ExtensionVersionEntity.builder()
                    .id("ext_grpc_levy@1.0.0")
                    .extensionId("ext_grpc_levy")
                    .name("gRPC Levy")
                    .version("1.0.0")
                    .extensionPoint("policy.pre-quote")
                    .ownerId("PLATFORM")
                    .contractVersion("2.1")
                    .runtimeType(RuntimeKind.WASM)
                    .runtimeConfigJson("{\"artifactToken\":\"vt_grpc_levy\"}")
                    .dataScopeJson("[\"quote.basic\"]")
                    .timeoutMs(1500)
                    .memoryMb(64)
                    .failMode(FailMode.FAIL_OPEN)
                    .secretsJson("[]")
                    .testVectorsJson("[]")
                    .description("Test gRPC levy extension")
                    .changelog("Initial")
                    .artifactHash("hash1")
                    .scanResult("PASSED")
                    .testsTotal(5).testsPassed(5).testsFailed(0)
                    .deterministic(true).maxLatencyMs(10L).maxMemoryMb(32)
                    .status(ExtensionStatus.ENABLED)
                    .etagVersion(1)
                    .createdBy("system")
                    .createdAt(now).updatedAt(now)
                    .build());
            enablementRepository.save(ExtensionEnablementEntity.builder()
                    .enablementId("enb_grpc_1")
                    .extensionId("ext_grpc_levy")
                    .extensionVersion("1.0.0")
                    .hookId("policy.pre-quote")
                    .tenantIdsJson("[\"ten_grpc\"]")
                    .productIdsJson("[]")
                    .regionCodesJson("[]")
                    .priority(10)
                    .failMode(FailMode.FAIL_OPEN)
                    .timeoutMs(1500)
                    .errorRatePercent(20)
                    .windowMinutes(5)
                    .consecutiveFailures(20)
                    .active(true)
                    .reason("gRPC integration test")
                    .enabledBy("system")
                    .effectiveBy(now)
                    .createdAt(now).updatedAt(now)
                    .build());

            sandboxRuntime.registerLocalHandler("ext_grpc_levy",
                    in -> Map.of("loadings", List.of(Map.of("code", "COOP_LEVY", "amountMinor", 3240))));

            InvokeExtensionResponse okResp = policyStub.invokeExtension(InvokeExtensionRequest.newBuilder()
                    .setHookId("policy.pre-quote")
                    .setContractVersion("2.1")
                    .setCallerService("policy-service")
                    .setContext(InvocationContext.newBuilder().setTenantId("ten_grpc").build())
                    .setSubjectRef("qt_90e1c4a7")
                    .setInput(ByteString.copyFromUtf8("{\"quote\":{\"premiumMinor\":324000}}"))
                    .setDeadlineMs(2000)
                    .build());
            assertThat(okResp.getStatus()).isEqualTo(InvokeStatus.OK);
            assertThat(okResp.getMerged().getPayloadJson().toStringUtf8()).contains("COOP_LEVY");
            assertThat(okResp.getExtensions(0).getResult()).isEqualTo(ExtensionExecutionResult.EXEC_OK);

            // INT-14: VALIDATE hook trying to RELEASE a hold -> RULE_VIOLATION + HOLD_FOR_REVIEW applied
            extensionVersionRepository.save(ExtensionVersionEntity.builder()
                    .id("ext_bad_val@1.0.0")
                    .extensionId("ext_bad_val")
                    .name("Bad Validator")
                    .version("1.0.0")
                    .extensionPoint("claim.validator")
                    .ownerId("PLATFORM")
                    .contractVersion("1.0")
                    .runtimeType(RuntimeKind.WASM)
                    .runtimeConfigJson("{\"artifactToken\":\"vt_bad_val\"}")
                    .dataScopeJson("[\"claim.basic\"]")
                    .timeoutMs(1500)
                    .memoryMb(64)
                    .failMode(FailMode.HOLD_FOR_REVIEW)
                    .secretsJson("[]")
                    .testVectorsJson("[]")
                    .description("Attempts forbidden RELEASE")
                    .changelog("Initial")
                    .artifactHash("hash2")
                    .scanResult("PASSED")
                    .testsTotal(5).testsPassed(5).testsFailed(0)
                    .deterministic(true).maxLatencyMs(10L).maxMemoryMb(32)
                    .status(ExtensionStatus.ENABLED)
                    .etagVersion(1)
                    .createdBy("system")
                    .createdAt(now).updatedAt(now)
                    .build());
            enablementRepository.save(ExtensionEnablementEntity.builder()
                    .enablementId("enb_grpc_2")
                    .extensionId("ext_bad_val")
                    .extensionVersion("1.0.0")
                    .hookId("claim.validator")
                    .tenantIdsJson("[\"ten_grpc\"]")
                    .productIdsJson("[]")
                    .regionCodesJson("[]")
                    .priority(10)
                    .failMode(FailMode.HOLD_FOR_REVIEW)
                    .timeoutMs(1500)
                    .errorRatePercent(20)
                    .windowMinutes(5)
                    .consecutiveFailures(20)
                    .active(true)
                    .reason("gRPC rule violation test")
                    .enabledBy("system")
                    .effectiveBy(now)
                    .createdAt(now).updatedAt(now)
                    .build());
            sandboxRuntime.registerLocalHandler("ext_bad_val", in -> Map.of("decision", "RELEASE"));

            ExtensionServiceGrpc.ExtensionServiceBlockingStub claimsStub = stubForService(channel, "claims-service");
            InvokeExtensionResponse violResp = claimsStub.invokeExtension(InvokeExtensionRequest.newBuilder()
                    .setHookId("claim.validator")
                    .setContractVersion("1.0")
                    .setCallerService("claims-service")
                    .setContext(InvocationContext.newBuilder().setTenantId("ten_grpc").build())
                    .setSubjectRef("clm_001")
                    .setInput(ByteString.copyFromUtf8("{\"claimId\":\"clm_001\"}"))
                    .setDeadlineMs(2000)
                    .build());
            assertThat(violResp.getStatus()).isEqualTo(InvokeStatus.HELD);
            assertThat(violResp.getFailModeApplied()).isEqualTo(FailModeApplied.HOLD_FOR_REVIEW);
            assertThat(violResp.getExtensions(0).getResult()).isEqualTo(ExtensionExecutionResult.RULE_VIOLATION);
        } finally {
            sandboxRuntime.clearLocalHandlers();
            channel.shutdownNow();
        }
    }
}
