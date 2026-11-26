//package com.umar;
//
//import com.google.protobuf.ByteString;
//import com.umar.api.grpc.VaultGrpcServer;
//import com.umar.grpc.vault.v1.*;
//import com.umar.payload.request.vault.CreateSecretRequest;
//import com.umar.service.SecretService;
//import io.grpc.ManagedChannel;
//import io.grpc.Metadata;
//import io.grpc.Status;
//import io.grpc.StatusRuntimeException;
//import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
//import io.grpc.stub.MetadataUtils;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.UUID;
//import java.util.concurrent.TimeUnit;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//
//@SpringBootTest(classes = VaultServiceApplication.class)
//@ActiveProfiles("test")
//class VaultGrpcIntegrationTest {
//
//    @Autowired
//    private VaultGrpcServer grpcServer;
//
//    @Autowired
//    private SecretService secretService;
//
//    @Test
//    void vlt11_grpcTokenizeDetokenizeHashSignAndPermissionDenied() throws Exception {
//        String signingRef = "jwt.grpc.test." + UUID.randomUUID().toString().substring(0, 6);
//        secretService.createSecret(CreateSecretRequest.builder()
//                .ref(signingRef)
//                .secretClass("SIGNING_KEY")
//                .mode("GENERATE")
//                .generate(CreateSecretRequest.GenerateSpec.builder()
//                        .algorithm("HMAC_SHA256")
//                        .keySizeBits(256)
//                        .build())
//                .policy(CreateSecretRequest.SecretPolicySpec.builder()
//                        .uses(List.of("SIGN", "VERIFY"))
//                        .services(List.of("AUTH-KYC-SERVICE"))
//                        .purposes(List.of("TOKEN_SIGNING"))
//                        .build())
//                .build());
//
//        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", grpcServer.getBoundPort())
//                .usePlaintext()
//                .build();
//        try {
//            Metadata authHeaders = new Metadata();
//            authHeaders.put(Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER),
//                    "AUTH-KYC-SERVICE");
//
//            VaultServiceGrpc.VaultServiceBlockingStub stub = VaultServiceGrpc.newBlockingStub(channel)
//                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders));
//
//            // 1. Hash
//            HashGrpcResponse hashRes = stub.hash(HashGrpcRequest.newBuilder()
//                    .setDataClass("PHONE")
//                    .setPurpose("PHONE_LOOKUP")
//                    .setContextRef("reg:grpc1")
//                    .addValues("+254712345000")
//                    .build());
//            assertThat(hashRes.getHashesCount()).isEqualTo(1);
//            assertThat(hashRes.getHashes(0).getEntriesCount()).isGreaterThanOrEqualTo(1);
//
//            // 2. Tokenize
//            String subj = "grpc-subj-" + UUID.randomUUID();
//            TokenizeGrpcResponse tokRes = stub.tokenize(TokenizeGrpcRequest.newBuilder()
//                    .setPurpose("ACCOUNT_REGISTRATION")
//                    .setContextRef("reg:grpc1")
//                    .setRequestId(UUID.randomUUID().toString())
//                    .addItems(TokenizeGrpcItem.newBuilder()
//                            .setDataClass("PHONE")
//                            .setValue("+254712345000")
//                            .setSubjectRef(subj)
//                            .build())
//                    .build());
//            assertThat(tokRes.getTokensCount()).isEqualTo(1);
//            String token = tokRes.getTokens(0).getToken();
//            assertThat(token).startsWith("vt_phone_");
//
//            // 3. Detokenize
//            DetokenizeGrpcResponse detokRes = stub.detokenize(DetokenizeGrpcRequest.newBuilder()
//                    .setPurpose("ACCOUNT_REGISTRATION")
//                    .setContextRef("reg:grpc1")
//                    .addTokens(token)
//                    .setReturnMode("VALUE")
//                    .build());
//            assertThat(detokRes.getValuesCount()).isEqualTo(1);
//            assertThat(detokRes.getValues(0).getValue()).isEqualTo("+254712345000");
//
//            // 4. Sign
//            SignGrpcResponse signRes = stub.sign(SignGrpcRequest.newBuilder()
//                    .setSecretRef(signingRef)
//                    .setAlgorithm("HMAC_SHA256")
//                    .setPurpose("TOKEN_SIGNING")
//                    .setContextRef("jwt:1")
//                    .setPayload(ByteString.copyFrom("header.payload".getBytes(StandardCharsets.UTF_8)))
//                    .build());
//            assertThat(signRes.getSignature().size()).isEqualTo(32);
//            assertThat(signRes.getKeyVersion()).isEqualTo(1);
//
//            // 5. Unauthorized service identity gets PERMISSION_DENIED
//            Metadata rogueHeaders = new Metadata();
//            rogueHeaders.put(Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER),
//                    "ROGUE-SERVICE");
//            VaultServiceGrpc.VaultServiceBlockingStub rogueStub = VaultServiceGrpc.newBlockingStub(channel)
//                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(rogueHeaders));
//
//            assertThatThrownBy(() -> rogueStub.detokenize(DetokenizeGrpcRequest.newBuilder()
//                    .setPurpose("ACCOUNT_REGISTRATION")
//                    .setContextRef("steal:1")
//                    .addTokens(token)
//                    .build()))
//                    .isInstanceOf(StatusRuntimeException.class)
//                    .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
//                            .isEqualTo(Status.Code.PERMISSION_DENIED));
//        } finally {
//            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
//        }
//    }
//}
