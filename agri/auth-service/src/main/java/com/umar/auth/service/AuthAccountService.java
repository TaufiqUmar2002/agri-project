package com.umar.auth.service;

import com.umar.auth.client.DataVaultClient;
import com.umar.auth.client.HookSdkClient;
import com.umar.auth.client.NotificationClient;
import com.umar.auth.config.AuthProperties;
import com.umar.auth.domain.entity.DelegationEntity;
import com.umar.auth.domain.entity.RefreshSessionEntity;
import com.umar.auth.domain.entity.UserAccountEntity;
import com.umar.auth.exception.AuthApiException;
import com.umar.auth.repository.DelegationRepository;
import com.umar.auth.repository.RefreshSessionRepository;
import com.umar.auth.repository.UserAccountRepository;
import com.umar.auth.security.AuthIdentity;
import com.umar.auth.security.AuthSecurityContext;
import com.umar.auth.security.CryptoHashService;
import com.umar.auth.security.JwtTokenService;
import com.umar.auth.service.support.IdempotencyService;
import com.umar.auth.service.support.OtpChallengeService;
import com.umar.auth.service.support.OutboxService;
import com.umar.auth.service.support.RateLimitService;
import com.umar.enums.auth.*;
import com.umar.payload.request.auth.*;
import com.umar.payload.response.auth.AuthTokenResponse;
import com.umar.payload.response.auth.RegisterAccountResponse;
import com.umar.payload.response.auth.VerifyOtpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthAccountService {

    private final UserAccountRepository userAccountRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final DelegationRepository delegationRepository;
    private final DataVaultClient dataVaultClient;
    private final NotificationClient notificationClient;
    private final HookSdkClient hookSdkClient;
    private final CryptoHashService cryptoHashService;
    private final JwtTokenService jwtTokenService;
    private final OtpChallengeService otpChallengeService;
    private final RateLimitService rateLimitService;
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final IdentityContextService identityContextService;
    private final AuthProperties properties;

    @Transactional
    public RegisterAccountResponse registerAccount(String idempotencyKey, String clientIp, RegisterAccountRequest request) {
        if (request.getUserType() == UserType.STAFF) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "REGISTRATION_NOT_ALLOWED",
                    "STAFF accounts cannot self-register");
        }
        if (!properties.getSecurity().getSupportedLanguages().contains(request.getPreferredLanguage().toLowerCase())) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "preferredLanguage is not in supported languages");
        }
        RegistrationChannel channel = request.getChannel() != null ? request.getChannel() : RegistrationChannel.APP;
        if (channel == RegistrationChannel.USSD) {
            if (request.getPin() == null || !request.getPin().matches("^\\d{4,6}$") || isWeakPin(request.getPin())) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Valid 4-6 digit non-sequential PIN is required for USSD registration");
            }
        }

        Optional<RegisterAccountResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "AUT-01", request, RegisterAccountResponse.class);
        if (replay.isPresent()) {
            return replay.get();
        }

        String phoneHash = dataVaultClient.computeKeyedHash(request.getPhoneNumber(), "PHONE_HASH");
        rateLimitService.checkAndIncrement("reg:phone:" + phoneHash,
                properties.getLimits().getRegisterPerPhonePerHour(), 3600);
        rateLimitService.checkAndIncrement("reg:ip:" + clientIp,
                properties.getLimits().getRegisterPerIpPerHour(), 3600);

        HookSdkClient.HookResult hook = hookSdkClient.invokePreRegister(phoneHash, channel.name(), "SELF");
        if (!hook.allowed()) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "REGISTRATION_NOT_ALLOWED",
                    "Registration is not available for this number");
        }

        Instant now = Instant.now();
        Optional<UserAccountEntity> existing = userAccountRepository.findByPhoneHash(phoneHash);

        UserAccountEntity account;
        boolean alreadyActive = false;
        if (existing.isPresent()) {
            account = existing.get();
            if (account.getStatus() == AccountStatus.ACTIVE || account.getStatus() == AccountStatus.PENDING_APPROVAL) {
                alreadyActive = true;
            } else if (account.getStatus() == AccountStatus.PENDING_OTP) {
                if (request.getPin() != null) {
                    account.setPinHash(cryptoHashService.hashSecret(request.getPin()));
                }
                account.setFullNameEnc(cryptoHashService.encryptField(request.getFullName()));
                account.setPreferredLanguage(request.getPreferredLanguage().toLowerCase());
                userAccountRepository.save(account);
            }
        } else {
            String phoneToken = dataVaultClient.tokenize("PHONE", request.getPhoneNumber(), phoneHash, "ACCOUNT_REGISTRATION");
            Set<AuthRole> initialRoles = EnumSet.of(
                    request.getUserType() == UserType.AGENT ? AuthRole.AGENT : AuthRole.FARMER);
            account = userAccountRepository.save(UserAccountEntity.builder()
                    .userId(UUID.randomUUID())
                    .userType(request.getUserType())
                    .phoneToken(phoneToken)
                    .phoneHash(phoneHash)
                    .fullNameEnc(cryptoHashService.encryptField(request.getFullName()))
                    .status(AccountStatus.PENDING_OTP)
                    .kycTier(KycTier.TIER_0)
                    .rolesCsv(initialRoles.iterator().next().name())
                    .pinHash(request.getPin() != null ? cryptoHashService.hashSecret(request.getPin()) : null)
                    .preferredLanguage(request.getPreferredLanguage().toLowerCase())
                    .registeredVia(RegistrationChannel.SELF)
                    .failedLoginCount(0)
                    .lockoutCount(0)
                    .createdAt(now)
                    .build());

            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", "user.registered");
            evt.put("userId", account.getUserId().toString());
            evt.put("userType", account.getUserType().name());
            evt.put("phoneToken", account.getPhoneToken());
            evt.put("channel", channel.name());
            evt.put("preferredLanguage", account.getPreferredLanguage());
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getIdentityTopic(), "user.registered",
                    account.getUserId().toString(), evt);
        }

        OtpChallengeService.CreatedChallenge challenge = otpChallengeService.createChallenge(
                account.getUserId(), phoneHash, OtpPurpose.REGISTRATION, null, null, null);
        String deliveryPayload = alreadyActive ? "ALREADY_REGISTERED_LOGIN_PROMPT" : challenge.rawOtp();
        String deliveryChannel = notificationClient.sendOtp(
                account.getPhoneToken(), deliveryPayload, account.getPreferredLanguage(), channel.name());

        RegisterAccountResponse response = RegisterAccountResponse.builder()
                .challengeId(challenge.challengeId())
                .maskedPhone(DelegationService.maskPhone(request.getPhoneNumber()))
                .expiresInSeconds(properties.getSecurity().getOtpTtlSeconds())
                .resendAfterSeconds(properties.getSecurity().getOtpResendSeconds())
                .channel(deliveryChannel)
                .build();

        idempotencyService.save(idempotencyKey, "AUT-01", request, 202, response);
        return response;
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public LoginResult login(String clientIp, LoginRequest request) {
        rateLimitService.checkAndIncrement("login:ip:" + clientIp, 60, 60);
        RegistrationChannel channel = request.getChannel() != null ? request.getChannel() : RegistrationChannel.APP;

        UserAccountEntity account;
        String accountKey;
        if (request.getGrantType() == GrantType.PASSWORD) {
            if (request.getUsername() == null || request.getUsername().isBlank()) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "username is required for PASSWORD");
            }
            accountKey = "user:" + request.getUsername().toLowerCase();
            rateLimitService.checkAndIncrement("login:acct:" + accountKey,
                    properties.getLimits().getLoginPerAccountPer15min(), 900);
            account = userAccountRepository.findByUsername(request.getUsername()).orElse(null);
        } else {
            if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "phoneNumber is required");
            }
            String phoneHash = dataVaultClient.computeKeyedHash(request.getPhoneNumber(), "PHONE_HASH");
            accountKey = "phone:" + phoneHash;
            rateLimitService.checkAndIncrement("login:acct:" + accountKey,
                    properties.getLimits().getLoginPerAccountPer15min(), 900);
            account = userAccountRepository.findByPhoneHash(phoneHash).orElse(null);
        }

        Instant now = Instant.now();
        if (account == null) {
            // Constant-time dummy work to mitigate enumeration
            cryptoHashService.verifySecret("dummy-pin", null);
            publishLoginFailed(accountKey, "INVALID_CREDENTIALS", channel, clientIp);
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
        }

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                    "Account suspended. Contact support", Map.of("reference", account.getUserId().toString()));
        }
        if (account.getStatus() == AccountStatus.LOCKED) {
            if (account.getLockedUntil() != null && now.isBefore(account.getLockedUntil())) {
                long retryAfter = Math.max(1L, account.getLockedUntil().getEpochSecond() - now.getEpochSecond());
                throw new AuthApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                        "Account temporarily locked", Map.of("retryAfter", retryAfter));
            }
            account.setStatus(AccountStatus.ACTIVE);
            account.setFailedLoginCount(0);
            account.setLockedUntil(null);
            userAccountRepository.save(account);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
        }

        if (request.getGrantType() == GrantType.OTP) {
            if (request.getOtp() == null || request.getChallengeId() == null) {
                OtpChallengeService.CreatedChallenge challenge = otpChallengeService.createChallenge(
                        account.getUserId(), account.getPhoneHash(), OtpPurpose.LOGIN, request.getDeviceId(), null, null);
                notificationClient.sendOtp(account.getPhoneToken(), challenge.rawOtp(), account.getPreferredLanguage(), channel.name());
                return new LoginResult(false, AuthTokenResponse.builder()
                        .challengeId(challenge.challengeId())
                        .maskedPhone(DelegationService.maskPhone(request.getPhoneNumber()))
                        .build());
            }
            OtpChallengeService.OtpChallenge ch;
            try {
                ch = otpChallengeService.verifyAndConsume(request.getChallengeId(), request.getOtp());
            } catch (AuthApiException ex) {
                recordLoginFailure(account, accountKey, channel, clientIp);
                throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
            }
            if (!ch.userId().equals(account.getUserId()) || ch.purpose() != OtpPurpose.LOGIN) {
                recordLoginFailure(account, accountKey, channel, clientIp);
                throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
            }
        } else if (request.getGrantType() == GrantType.PIN) {
            if (request.getPin() == null || !cryptoHashService.verifySecret(request.getPin(), account.getPinHash())) {
                recordLoginFailure(account, accountKey, channel, clientIp);
                throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
            }
        } else if (request.getGrantType() == GrantType.PASSWORD) {
            if (account.getUserType() != UserType.STAFF) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "PASSWORD grant is allowed for STAFF accounts only");
            }
            if (request.getPassword() == null || !cryptoHashService.verifySecret(request.getPassword(), account.getPasswordHash())) {
                recordLoginFailure(account, accountKey, channel, clientIp);
                throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
            }
            if (request.getMfaCode() == null || !isValidStaffMfa(account, request.getMfaCode())) {
                throw new AuthApiException(HttpStatus.UNAUTHORIZED, "MFA_REQUIRED",
                        "MFA code required", Map.of("mfaRequired", true));
            }
        }

        account.setFailedLoginCount(0);
        account.setLastLoginAt(now);
        userAccountRepository.save(account);

        AuthTokenResponse tokenResponse = issueSessionTokens(account, request.getDeviceId(), channel);
        hookSdkClient.postLogin(account.getUserId().toString(), channel.name());

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "auth.login.succeeded");
        evt.put("userId", account.getUserId().toString());
        evt.put("channel", channel.name());
        evt.put("deviceId", request.getDeviceId());
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getSecurityTopic(), "auth.login.succeeded",
                account.getUserId().toString(), evt);

        return new LoginResult(true, tokenResponse);
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public VerifyOtpResponse verifyOtp(String clientIp, VerifyOtpRequest request) {
        rateLimitService.checkAndIncrement("otp:ip:" + clientIp, 60, 3600);
        rateLimitService.checkAndIncrement("otp:ch:" + request.getChallengeId(), 10, 3600);

        OtpChallengeService.OtpChallenge ch;
        try {
            ch = otpChallengeService.verifyAndConsume(request.getChallengeId(), request.getOtp());
        } catch (AuthApiException ex) {
            if ("INVALID_OTP".equals(ex.getCode())) {
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("eventType", "auth.otp.failed");
                evt.put("challengeRef", request.getChallengeId());
                evt.put("ipHash", cryptoHashService.hmacSha256(clientIp));
                evt.put("timestamp", Instant.now().toString());
                outboxService.enqueue(properties.getKafka().getSecurityTopic(), "auth.otp.failed",
                        request.getChallengeId(), evt);
            }
            throw ex;
        }

        Instant now = Instant.now();
        UserAccountEntity user = userAccountRepository.findById(ch.userId())
                .orElseThrow(() -> new AuthApiException(HttpStatus.GONE, "CHALLENGE_EXPIRED",
                        "This code is no longer valid. Request a new one"));

        if (ch.purpose() == OtpPurpose.REGISTRATION) {
            if (user.getUserType() == UserType.AGENT) {
                user.setStatus(AccountStatus.PENDING_APPROVAL);
            } else {
                user.setStatus(AccountStatus.ACTIVE);
                user.setActivatedAt(now);
            }
            user.setKycTier(KycTier.TIER_0);
            userAccountRepository.save(user);
            identityContextService.evict(user.getUserId());

            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", "user.activated");
            evt.put("userId", user.getUserId().toString());
            evt.put("userType", user.getUserType().name());
            evt.put("kycTier", user.getKycTier().name());
            evt.put("activatedVia", "OTP");
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getIdentityTopic(), "user.activated",
                    user.getUserId().toString(), evt);

            VerifyOtpResponse.VerifyOtpResponseBuilder builder = VerifyOtpResponse.builder()
                    .purpose(OtpPurpose.REGISTRATION.name())
                    .userId(user.getUserId().toString())
                    .status(user.getStatus().name())
                    .kycTier(user.getKycTier().name());
            if (user.getUserType() == UserType.FARMER) {
                AuthTokenResponse tokens = issueSessionTokens(user, request.getDeviceId(), RegistrationChannel.APP);
                builder.accessToken(tokens.getAccessToken())
                        .refreshToken(tokens.getRefreshToken())
                        .expiresIn(tokens.getExpiresIn())
                        .tokenType(tokens.getTokenType());
            }
            return builder.build();
        } else if (ch.purpose() == OtpPurpose.AGENT_DELEGATION) {
            if (user.getStatus() == AccountStatus.PENDING_OTP) {
                user.setStatus(AccountStatus.ACTIVE);
                user.setActivatedAt(now);
                userAccountRepository.save(user);

                Map<String, Object> uEvt = new LinkedHashMap<>();
                uEvt.put("eventType", "user.activated");
                uEvt.put("userId", user.getUserId().toString());
                uEvt.put("userType", user.getUserType().name());
                uEvt.put("kycTier", user.getKycTier().name());
                uEvt.put("activatedVia", "AGENT_DELEGATION");
                uEvt.put("timestamp", now.toString());
                outboxService.enqueue(properties.getKafka().getIdentityTopic(), "user.activated",
                        user.getUserId().toString(), uEvt);
            }
            DelegationEntity delegation = delegationRepository.findById(ch.delegationId())
                    .orElseThrow(() -> new AuthApiException(HttpStatus.GONE, "CHALLENGE_EXPIRED",
                            "This code is no longer valid. Request a new one"));
            Instant validUntil = now.plus(delegation.getValidityHours(), ChronoUnit.HOURS);
            delegation.setStatus(DelegationStatus.ACTIVE);
            delegation.setConfirmedAt(now);
            delegation.setValidUntil(validUntil);
            delegationRepository.save(delegation);

            Map<String, Object> dEvt = new LinkedHashMap<>();
            dEvt.put("eventType", "agent.delegation.activated");
            dEvt.put("delegationId", delegation.getDelegationId().toString());
            dEvt.put("agentId", delegation.getAgentId().toString());
            dEvt.put("farmerUserId", delegation.getFarmerUserId().toString());
            dEvt.put("scopes", delegation.getScopes().stream().map(Enum::name).toList());
            dEvt.put("validUntil", validUntil.toString());
            dEvt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getIdentityTopic(), "agent.delegation.activated",
                    delegation.getDelegationId().toString(), dEvt);

            return VerifyOtpResponse.builder()
                    .purpose(OtpPurpose.AGENT_DELEGATION.name())
                    .userId(user.getUserId().toString())
                    .status(user.getStatus().name())
                    .kycTier(user.getKycTier().name())
                    .delegationId(delegation.getDelegationId().toString())
                    .delegationStatus(DelegationStatus.ACTIVE.name())
                    .validUntil(validUntil.toString())
                    .build();
        } else if (ch.purpose() == OtpPurpose.STEP_UP) {
            String stepUpToken = otpChallengeService.issueStepUpToken(user.getUserId(), ch.actionClass());
            return VerifyOtpResponse.builder()
                    .purpose(OtpPurpose.STEP_UP.name())
                    .userId(user.getUserId().toString())
                    .stepUpToken(stepUpToken)
                    .stepUpExpiresIn(properties.getSecurity().getStepUpTtlSeconds())
                    .build();
        }
        throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported challenge purpose");
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public AuthTokenResponse refreshToken(String clientIp, RefreshTokenRequest request) {
        rateLimitService.checkAndIncrement("refresh:ip:" + clientIp, 120, 60);
        String tokenHash = cryptoHashService.hmacSha256("RT:" + request.getRefreshToken());
        RefreshSessionEntity session = refreshSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Please log in again"));

        if (session.getChannel() == RegistrationChannel.USSD) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "NOT_REFRESHABLE",
                    "This session cannot be refreshed");
        }

        Instant now = Instant.now();
        if ("USED".equals(session.getStatus())) {
            // Check 10-second network retry grace window? Spec notes: reuse revokes whole family
            revokeFamily(session.getSessionFamilyId(), session.getUserId(), "TOKEN_REUSE");
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "TOKEN_REUSED",
                    "Session ended for your security. Please log in again");
        }
        if ("REVOKED".equals(session.getStatus())
                || now.isAfter(session.getExpiresAt())
                || now.isAfter(session.getIdleExpiresAt())) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                    "Please log in again");
        }

        if (session.getDeviceId() != null && request.getDeviceId() != null
                && !session.getDeviceId().equals(request.getDeviceId())) {
            session.setSuspicious(true);
            refreshSessionRepository.save(session);
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "DEVICE_MISMATCH",
                    "Please log in again on this device");
        }

        UserAccountEntity account = userAccountRepository.findById(session.getUserId())
                .orElseThrow(() -> new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Please log in again"));
        if (account.getStatus() == AccountStatus.SUSPENDED) {
            revokeFamily(session.getSessionFamilyId(), account.getUserId(), "SUSPENSION");
            throw new AuthApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "Account suspended. Contact support");
        }
        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new AuthApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Account temporarily locked");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Please log in again");
        }

        String nextRawToken = cryptoHashService.randomOpaqueToken("rt_");
        UUID nextTokenId = UUID.randomUUID();
        session.setStatus("USED");
        session.setLastUsedAt(now);
        session.setReplacedBy(nextTokenId);
        refreshSessionRepository.save(session);

        Instant nextIdle = account.getUserType() == UserType.STAFF
                ? session.getExpiresAt()
                : now.plus(properties.getSecurity().getFarmerRefreshIdleDays(), ChronoUnit.DAYS);

        RefreshSessionEntity nextSession = refreshSessionRepository.save(RefreshSessionEntity.builder()
                .tokenId(nextTokenId)
                .sessionFamilyId(session.getSessionFamilyId())
                .tokenHash(cryptoHashService.hmacSha256("RT:" + nextRawToken))
                .userId(account.getUserId())
                .deviceId(session.getDeviceId())
                .channel(session.getChannel())
                .status("ACTIVE")
                .suspicious(false)
                .issuedAt(now)
                .expiresAt(session.getExpiresAt())
                .idleExpiresAt(nextIdle)
                .build());

        int accessTtl = account.getUserType() == UserType.STAFF
                ? properties.getSecurity().getStaffAccessTtlSeconds()
                : properties.getSecurity().getFarmerAccessTtlSeconds();
        Map<String, Object> extra = hookSdkClient.enrichTokenClaims(account.getUserId().toString(), account.getUserType().name());
        String jwt = jwtTokenService.issueAccessToken(
                account.getUserId(), account.getRoles(), account.getKycTier().name(),
                account.getPreferredLanguage(), nextSession.getSessionFamilyId(), accessTtl, false, extra);

        return AuthTokenResponse.builder()
                .accessToken(jwt)
                .refreshToken(nextRawToken)
                .expiresIn(accessTtl)
                .tokenType("Bearer")
                .user(AuthTokenResponse.UserSummary.builder()
                        .userId(account.getUserId().toString())
                        .roles(account.getRoles().stream().map(Enum::name).sorted().toList())
                        .kycTier(account.getKycTier().name())
                        .preferredLanguage(account.getPreferredLanguage())
                        .build())
                .build();
    }

    public RegisterAccountResponse requestStepUpChallenge(StepUpRequest request) {
        AuthIdentity actor = AuthSecurityContext.require();
        if (actor.userId() == null) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Missing or expired token");
        }
        UserAccountEntity account = userAccountRepository.findById(actor.userId())
                .orElseThrow(() -> new AuthApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "User not found"));

        OtpChallengeService.CreatedChallenge ch = otpChallengeService.createChallenge(
                account.getUserId(), account.getPhoneHash(), OtpPurpose.STEP_UP,
                request.getDeviceId(), null, request.getActionClass());
        String chName = notificationClient.sendOtp(account.getPhoneToken(), ch.rawOtp(), account.getPreferredLanguage(), "SMS");

        return RegisterAccountResponse.builder()
                .challengeId(ch.challengeId())
                .maskedPhone("****")
                .expiresInSeconds(properties.getSecurity().getOtpTtlSeconds())
                .resendAfterSeconds(properties.getSecurity().getOtpResendSeconds())
                .channel(chName)
                .build();
    }

    public Map<String, Object> getJwks() {
        return jwtTokenService.getJwks();
    }

    private AuthTokenResponse issueSessionTokens(UserAccountEntity account, String deviceId, RegistrationChannel channel) {
        Instant now = Instant.now();
        UUID familyId = UUID.randomUUID();
        boolean isUssd = channel == RegistrationChannel.USSD;
        boolean isStaff = account.getUserType() == UserType.STAFF;
        int accessTtl = isStaff
                ? properties.getSecurity().getStaffAccessTtlSeconds()
                : properties.getSecurity().getFarmerAccessTtlSeconds();

        String rawRefresh = isUssd ? null : cryptoHashService.randomOpaqueToken("rt_");
        if (!isUssd) {
            Instant absExp = isStaff
                    ? now.plus(properties.getSecurity().getStaffRefreshAbsoluteHours(), ChronoUnit.HOURS)
                    : now.plus(properties.getSecurity().getFarmerRefreshAbsoluteDays(), ChronoUnit.DAYS);
            Instant idleExp = isStaff
                    ? absExp
                    : now.plus(properties.getSecurity().getFarmerRefreshIdleDays(), ChronoUnit.DAYS);
            refreshSessionRepository.save(RefreshSessionEntity.builder()
                    .tokenId(UUID.randomUUID())
                    .sessionFamilyId(familyId)
                    .tokenHash(cryptoHashService.hmacSha256("RT:" + rawRefresh))
                    .userId(account.getUserId())
                    .deviceId(deviceId)
                    .channel(channel)
                    .status("ACTIVE")
                    .suspicious(false)
                    .issuedAt(now)
                    .expiresAt(absExp)
                    .idleExpiresAt(idleExp)
                    .build());
        }

        Map<String, Object> extra = hookSdkClient.enrichTokenClaims(account.getUserId().toString(), account.getUserType().name());
        String accessToken = jwtTokenService.issueAccessToken(
                account.getUserId(), account.getRoles(), account.getKycTier().name(),
                account.getPreferredLanguage(), familyId, accessTtl, isUssd, extra);

        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefresh)
                .expiresIn(accessTtl)
                .tokenType("Bearer")
                .user(AuthTokenResponse.UserSummary.builder()
                        .userId(account.getUserId().toString())
                        .roles(account.getRoles().stream().map(Enum::name).sorted().toList())
                        .kycTier(account.getKycTier().name())
                        .preferredLanguage(account.getPreferredLanguage())
                        .build())
                .build();
    }

    private void recordLoginFailure(UserAccountEntity account, String accountRef, RegistrationChannel channel, String clientIp) {
        int next = account.getFailedLoginCount() + 1;
        account.setFailedLoginCount(next);
        Instant now = Instant.now();
        if (next >= properties.getSecurity().getLoginMaxFailures()) {
            int lockouts = account.getLockoutCount() + 1;
            account.setLockoutCount(lockouts);
            account.setStatus(AccountStatus.LOCKED);
            long dur = lockouts > 1
                    ? properties.getSecurity().getEscalatedLockoutSeconds()
                    : properties.getSecurity().getLockoutSeconds();
            Instant lockedUntil = now.plusSeconds(dur);
            account.setLockedUntil(lockedUntil);
            identityContextService.evict(account.getUserId());

            Map<String, Object> lockEvt = new LinkedHashMap<>();
            lockEvt.put("eventType", "user.locked");
            lockEvt.put("userId", account.getUserId().toString());
            lockEvt.put("reason", "MAX_LOGIN_FAILURES");
            lockEvt.put("lockedUntil", lockedUntil.toString());
            lockEvt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getIdentityTopic(), "user.locked",
                    account.getUserId().toString(), lockEvt);
            notificationClient.sendSecurityAlert(account.getPhoneToken(), "ACCOUNT_LOCKED", account.getPreferredLanguage());
        }
        userAccountRepository.save(account);
        publishLoginFailed(accountRef, "INVALID_CREDENTIALS", channel, clientIp);
    }

    private void publishLoginFailed(String accountRef, String reason, RegistrationChannel channel, String clientIp) {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "auth.login.failed");
        evt.put("accountRef", accountRef);
        evt.put("reason", reason);
        evt.put("channel", channel.name());
        evt.put("ipHash", cryptoHashService.hmacSha256(clientIp));
        evt.put("timestamp", Instant.now().toString());
        outboxService.enqueue(properties.getKafka().getSecurityTopic(), "auth.login.failed", accountRef, evt);
    }

    private void revokeFamily(UUID familyId, UUID userId, String reason) {
        List<RefreshSessionEntity> members = refreshSessionRepository.findBySessionFamilyId(familyId);
        for (RefreshSessionEntity m : members) {
            m.setStatus("REVOKED");
        }
        refreshSessionRepository.saveAll(members);

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "auth.session.revoked");
        evt.put("sessionFamilyId", familyId.toString());
        evt.put("userId", userId.toString());
        evt.put("reason", reason);
        evt.put("timestamp", Instant.now().toString());
        outboxService.enqueue(properties.getKafka().getSecurityTopic(), "auth.session.revoked",
                userId.toString(), evt);
    }

    private boolean isValidStaffMfa(UserAccountEntity account, String code) {
        return code != null && code.matches("^\\d{6}$") && !"000000".equals(code);
    }

    private boolean isWeakPin(String pin) {
        return pin.chars().distinct().count() == 1 || "1234".equals(pin) || "123456".equals(pin) || "012345".equals(pin);
    }

    public record LoginResult(boolean completed, AuthTokenResponse response) {}
}
