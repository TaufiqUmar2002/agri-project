package com.umar;

import com.umar.auth.api.grpc.AuthGrpcServer;
import com.umar.auth.domain.entity.ConsentRecordEntity;
import com.umar.auth.domain.entity.DelegationEntity;
import com.umar.auth.domain.entity.UserAccountEntity;
import com.umar.auth.repository.ConsentRecordRepository;
import com.umar.auth.repository.DelegationRepository;
import com.umar.auth.repository.UserAccountRepository;
import com.umar.auth.security.CryptoHashService;
import com.umar.enums.auth.*;
import com.umar.grpc.auth.v1.*;
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

@SpringBootTest(classes = AuthServiceApplication.class)
@ActiveProfiles("test")
class AuthKycGrpcTest {

    @Autowired
    private AuthGrpcServer grpcServer;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    @Autowired
    private DelegationRepository delegationRepository;

    @Autowired
    private CryptoHashService cryptoHashService;

    private IdentityServiceGrpc.IdentityServiceBlockingStub stub(ManagedChannel ch, String caller) {
        Metadata headers = new Metadata();
        if (caller != null) {
            headers.put(Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER), caller);
        }
        return IdentityServiceGrpc.newBlockingStub(ch)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @Test
    void aut12_and_aut13_identityContextAndValidateDelegation() {
        UUID farmerId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        Instant now = Instant.now();

        userAccountRepository.save(UserAccountEntity.builder()
                .userId(farmerId)
                .userType(UserType.FARMER)
                .phoneToken("vt_p_1")
                .phoneHash(cryptoHashService.hmacSha256("grpc-f-" + farmerId))
                .fullNameEnc("enc")
                .status(AccountStatus.ACTIVE)
                .kycTier(KycTier.TIER_1)
                .rolesCsv("FARMER")
                .preferredLanguage("sw")
                .failedLoginCount(0)
                .lockoutCount(0)
                .createdAt(now)
                .build());

        userAccountRepository.save(UserAccountEntity.builder()
                .userId(agentId)
                .userType(UserType.AGENT)
                .phoneToken("vt_p_2")
                .phoneHash(cryptoHashService.hmacSha256("grpc-a-" + agentId))
                .fullNameEnc("enc")
                .status(AccountStatus.ACTIVE)
                .kycTier(KycTier.TIER_1)
                .rolesCsv("AGENT")
                .preferredLanguage("sw")
                .failedLoginCount(0)
                .lockoutCount(0)
                .createdAt(now)
                .build());

        consentRecordRepository.save(ConsentRecordEntity.builder()
                .consentId(UUID.randomUUID())
                .userId(farmerId)
                .purpose(ConsentPurpose.AUTOMATED_PAYOUT)
                .textVersion("3")
                .language("sw")
                .channel(RegistrationChannel.APP)
                .evidenceType(ConsentEvidenceType.TAP_CONFIRM)
                .capturedBy(farmerId)
                .status(ConsentStatus.ACTIVE)
                .grantedAt(now)
                .build());

        UUID delegationId = UUID.randomUUID();
        delegationRepository.save(DelegationEntity.builder()
                .delegationId(delegationId)
                .agentId(agentId)
                .farmerUserId(farmerId)
                .scopesCsv("CONSENT_CAPTURE,KYC_SUBMIT")
                .status(DelegationStatus.ACTIVE)
                .validityHours(24)
                .confirmationExpiresAt(now.plus(15, ChronoUnit.MINUTES))
                .confirmedAt(now)
                .validUntil(now.plus(24, ChronoUnit.HOURS))
                .createdAt(now)
                .build());

        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", grpcServer.getBoundPort())
                .usePlaintext()
                .build();
        try {
            assertThatThrownBy(() -> stub(channel, null).getIdentityContext(
                    GetIdentityContextRequest.newBuilder().setUserId(farmerId.toString()).build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);

            assertThatThrownBy(() -> stub(channel, "rogue-service").getIdentityContext(
                    GetIdentityContextRequest.newBuilder().setUserId(farmerId.toString()).build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.PERMISSION_DENIED);

            IdentityServiceGrpc.IdentityServiceBlockingStub policyStub = stub(channel, "policy-service");
            GetIdentityContextResponse ctx = policyStub.getIdentityContext(
                    GetIdentityContextRequest.newBuilder()
                            .setUserId(farmerId.toString())
                            .addPurposes("AUTOMATED_PAYOUT")
                            .build());
            assertThat(ctx.getAccountStatus()).isEqualTo(GrpcAccountStatus.ACTIVE);
            assertThat(ctx.getKycTier()).isEqualTo(GrpcKycTier.TIER_1);
            assertThat(ctx.getConsentsList()).hasSize(1);
            assertThat(ctx.getConsents(0).getStatus()).isEqualTo(GrpcConsentState.CONSENT_ACTIVE);

            ValidateDelegationResponse validResp = policyStub.validateDelegation(
                    ValidateDelegationRequest.newBuilder()
                            .setDelegationId(delegationId.toString())
                            .setAgentId(agentId.toString())
                            .setFarmerUserId(farmerId.toString())
                            .setRequiredScope(GrpcDelegationScope.KYC_SUBMIT)
                            .build());
            assertThat(validResp.getValid()).isTrue();
            assertThat(validResp.getReason()).isEqualTo(GrpcDelegationReason.NONE);

            ValidateDelegationResponse wrongScope = policyStub.validateDelegation(
                    ValidateDelegationRequest.newBuilder()
                            .setDelegationId(delegationId.toString())
                            .setAgentId(agentId.toString())
                            .setFarmerUserId(farmerId.toString())
                            .setRequiredScope(GrpcDelegationScope.PLOT_REGISTER)
                            .build());
            assertThat(wrongScope.getValid()).isFalse();
            assertThat(wrongScope.getReason()).isEqualTo(GrpcDelegationReason.SCOPE_NOT_GRANTED);
        } finally {
            channel.shutdownNow();
        }
    }
}
