package com.umar.auth.service;

import com.umar.auth.client.DataVaultClient;
import com.umar.auth.client.HookSdkClient;
import com.umar.auth.client.NotificationClient;
import com.umar.auth.config.AuthProperties;
import com.umar.auth.domain.entity.DelegationEntity;
import com.umar.auth.domain.entity.UserAccountEntity;
import com.umar.auth.exception.AuthApiException;
import com.umar.auth.repository.DelegationRepository;
import com.umar.auth.repository.UserAccountRepository;
import com.umar.auth.security.AuthIdentity;
import com.umar.auth.security.AuthSecurityContext;
import com.umar.auth.security.CryptoHashService;
import com.umar.auth.service.support.IdempotencyService;
import com.umar.auth.service.support.OtpChallengeService;
import com.umar.auth.service.support.OutboxService;
import com.umar.enums.auth.*;
import com.umar.payload.request.auth.EnrollOnBehalfRequest;
import com.umar.payload.response.auth.EnrollOnBehalfResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DelegationService {

    private final DelegationRepository delegationRepository;
    private final UserAccountRepository userAccountRepository;
    private final DataVaultClient dataVaultClient;
    private final NotificationClient notificationClient;
    private final HookSdkClient hookSdkClient;
    private final CryptoHashService cryptoHashService;
    private final OtpChallengeService otpChallengeService;
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final AuthProperties properties;

    @Transactional
    public EnrollOnBehalfResponse enrollOnBehalf(UUID pathAgentId, String idempotencyKey, EnrollOnBehalfRequest request) {
        AuthIdentity actor = AuthSecurityContext.require();
        if (!actor.hasRole(AuthRole.AGENT) || actor.userId() == null || !actor.userId().equals(pathAgentId)) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }

        Optional<EnrollOnBehalfResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "AUT-06", request, EnrollOnBehalfResponse.class);
        if (replay.isPresent()) {
            return replay.get();
        }

        UserAccountEntity agent = userAccountRepository.findById(pathAgentId)
                .orElseThrow(() -> new AuthApiException(HttpStatus.FORBIDDEN, "AGENT_NOT_ACTIVE",
                        "Your agent account is not active"));
        if (agent.getStatus() != AccountStatus.ACTIVE || !agent.getRoles().contains(AuthRole.AGENT)) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "AGENT_NOT_ACTIVE",
                    "Your agent account is not active");
        }

        Instant dayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        long attempts = delegationRepository.countByAgentIdAndCreatedAtAfter(pathAgentId, dayAgo);
        long confirmed = delegationRepository.countByAgentIdAndStatusAndConfirmedAtAfter(
                pathAgentId, DelegationStatus.ACTIVE, dayAgo);
        if (attempts >= properties.getLimits().getAgentDailyAttempts()
                || confirmed >= properties.getLimits().getAgentDailyConfirmations()) {
            throw new AuthApiException(HttpStatus.TOO_MANY_REQUESTS, "AGENT_LIMIT_REACHED",
                    "Daily enrollment limit reached", Map.of("retryAfterSeconds", 3600));
        }

        if (!properties.getSecurity().getSupportedLanguages().contains(request.getPreferredLanguage().toLowerCase())) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported preferredLanguage");
        }

        String phoneHash = dataVaultClient.computeKeyedHash(request.getFarmerPhoneNumber(), "PHONE_HASH");
        if (agent.getPhoneHash() != null && agent.getPhoneHash().equals(phoneHash)) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "REGISTRATION_NOT_ALLOWED",
                    "Agent cannot enroll himself as a farmer");
        }

        HookSdkClient.HookResult hookResult = hookSdkClient.invokePreRegister(phoneHash, "AGENT", "AGENT");
        if (!hookResult.allowed()) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "REGISTRATION_NOT_ALLOWED",
                    "Enrollment is not available for this number");
        }

        String phoneToken = dataVaultClient.tokenize("PHONE", request.getFarmerPhoneNumber(), phoneHash, "ACCOUNT_REGISTRATION");
        Instant now = Instant.now();

        boolean createdNewAccount = false;
        UserAccountEntity farmer = userAccountRepository.findByPhoneHash(phoneHash).orElse(null);
        if (farmer == null) {
            createdNewAccount = true;
            farmer = userAccountRepository.save(UserAccountEntity.builder()
                    .userId(UUID.randomUUID())
                    .userType(UserType.FARMER)
                    .phoneToken(phoneToken)
                    .phoneHash(phoneHash)
                    .fullNameEnc(cryptoHashService.encryptField(request.getFarmerFullName()))
                    .status(AccountStatus.PENDING_OTP)
                    .kycTier(KycTier.TIER_0)
                    .rolesCsv(AuthRole.FARMER.name())
                    .preferredLanguage(request.getPreferredLanguage().toLowerCase())
                    .registeredVia(RegistrationChannel.AGENT)
                    .agentId(pathAgentId)
                    .cooperativeId(request.getCooperativeId())
                    .failedLoginCount(0)
                    .lockoutCount(0)
                    .createdAt(now)
                    .build());
        }

        int validityHours = request.getRequestedValidityHours() != null ? request.getRequestedValidityHours() : 24;
        Instant confirmationExpiresAt = now.plus(15, ChronoUnit.MINUTES);
        DelegationEntity delegation = delegationRepository.save(DelegationEntity.builder()
                .delegationId(UUID.randomUUID())
                .agentId(pathAgentId)
                .farmerUserId(farmer.getUserId())
                .scopesCsv(request.getScopes().stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse(""))
                .status(DelegationStatus.PENDING_FARMER_CONFIRMATION)
                .cooperativeId(request.getCooperativeId())
                .locationLat(request.getEnrollmentLocation().getLat())
                .locationLon(request.getEnrollmentLocation().getLon())
                .locationAccuracy(request.getEnrollmentLocation().getAccuracyMeters())
                .validityHours(validityHours)
                .confirmationExpiresAt(confirmationExpiresAt)
                .createdAt(now)
                .build());

        OtpChallengeService.CreatedChallenge challenge = otpChallengeService.createChallenge(
                farmer.getUserId(), phoneHash, OtpPurpose.AGENT_DELEGATION,
                request.getDeviceId(), delegation.getDelegationId(), null);

        notificationClient.sendOtp(farmer.getPhoneToken(), challenge.rawOtp(), farmer.getPreferredLanguage(), "SMS");

        Map<String, Object> delEvt = new LinkedHashMap<>();
        delEvt.put("eventType", "agent.delegation.requested");
        delEvt.put("delegationId", delegation.getDelegationId().toString());
        delEvt.put("agentId", pathAgentId.toString());
        delEvt.put("scopes", request.getScopes().stream().map(Enum::name).toList());
        delEvt.put("cooperativeId", request.getCooperativeId());
        delEvt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getSecurityTopic(), "agent.delegation.requested",
                delegation.getDelegationId().toString(), delEvt);

        if (createdNewAccount) {
            Map<String, Object> regEvt = new LinkedHashMap<>();
            regEvt.put("eventType", "user.registered");
            regEvt.put("userId", farmer.getUserId().toString());
            regEvt.put("userType", UserType.FARMER.name());
            regEvt.put("phoneToken", farmer.getPhoneToken());
            regEvt.put("channel", RegistrationChannel.AGENT.name());
            regEvt.put("registeredVia", RegistrationChannel.AGENT.name());
            regEvt.put("agentId", pathAgentId.toString());
            regEvt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getIdentityTopic(), "user.registered",
                    farmer.getUserId().toString(), regEvt);
        }

        EnrollOnBehalfResponse response = EnrollOnBehalfResponse.builder()
                .delegationId(delegation.getDelegationId().toString())
                .status(DelegationStatus.PENDING_FARMER_CONFIRMATION.name())
                .challengeId(challenge.challengeId())
                .maskedPhone(maskPhone(request.getFarmerPhoneNumber()))
                .confirmationExpiresAt(confirmationExpiresAt.toString())
                .scopes(request.getScopes().stream().map(Enum::name).toList())
                .validityHours(validityHours)
                .build();

        idempotencyService.save(idempotencyKey, "AUT-06", request, 202, response);
        return response;
    }

    public ValidationResult validateDelegation(UUID delegationId, UUID agentId, UUID farmerUserId, DelegationScope requiredScope) {
        Optional<DelegationEntity> opt = delegationRepository.findById(delegationId);
        if (opt.isEmpty()) {
            return new ValidationResult(false, DelegationValidationReason.NOT_FOUND, null, Set.of());
        }
        DelegationEntity d = opt.get();
        if (!d.getAgentId().equals(agentId) || !d.getFarmerUserId().equals(farmerUserId)) {
            return new ValidationResult(false, DelegationValidationReason.MISMATCH, null, Set.of());
        }
        if (d.getStatus() == DelegationStatus.PENDING_FARMER_CONFIRMATION) {
            return new ValidationResult(false, DelegationValidationReason.NOT_CONFIRMED, null, d.getScopes());
        }
        if (d.getStatus() == DelegationStatus.REVOKED) {
            return new ValidationResult(false, DelegationValidationReason.REVOKED_BY_FARMER, null, d.getScopes());
        }
        Instant now = Instant.now();
        if (d.getStatus() == DelegationStatus.EXPIRED || (d.getValidUntil() != null && now.isAfter(d.getValidUntil()))) {
            return new ValidationResult(false, DelegationValidationReason.EXPIRED, null, d.getScopes());
        }
        if (!d.getScopes().contains(requiredScope)) {
            return new ValidationResult(false, DelegationValidationReason.SCOPE_NOT_GRANTED, d.getValidUntil(), d.getScopes());
        }
        UserAccountEntity agent = userAccountRepository.findById(agentId).orElse(null);
        if (agent == null || agent.getStatus() != AccountStatus.ACTIVE) {
            return new ValidationResult(false, DelegationValidationReason.AGENT_NOT_ACTIVE, null, d.getScopes());
        }
        return new ValidationResult(true, DelegationValidationReason.NONE, d.getValidUntil(), d.getScopes());
    }

    public UUID requireActiveDelegationForAgent(UUID delegationId, UUID agentId, DelegationScope requiredScope) {
        DelegationEntity d = delegationRepository.findById(delegationId)
                .orElseThrow(() -> new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised"));
        ValidationResult res = validateDelegation(delegationId, agentId, d.getFarmerUserId(), requiredScope);
        if (!res.valid()) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        return d.getFarmerUserId();
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return "****";
        return phone.substring(0, 5) + "*****" + phone.substring(phone.length() - 3);
    }

    public record ValidationResult(
            boolean valid,
            DelegationValidationReason reason,
            Instant validUntil,
            Set<DelegationScope> grantedScopes) {}
}
