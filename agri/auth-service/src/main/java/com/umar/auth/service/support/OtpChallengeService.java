package com.umar.auth.service.support;

import com.umar.auth.config.AuthProperties;
import com.umar.auth.exception.AuthApiException;
import com.umar.auth.security.CryptoHashService;
import com.umar.enums.auth.OtpPurpose;
import com.umar.enums.auth.StepUpActionClass;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OtpChallengeService {

    private final CryptoHashService cryptoHashService;
    private final AuthProperties properties;

    private final Map<String, OtpChallenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, StepUpTokenRecord> stepUpTokens = new ConcurrentHashMap<>();

    public CreatedChallenge createChallenge(
            UUID userId,
            String phoneHash,
            OtpPurpose purpose,
            String deviceHint,
            UUID delegationId,
            StepUpActionClass actionClass) {

        // Invalidate older active challenges for same phoneHash + purpose (so only one valid code exists at a time)
        challenges.entrySet().removeIf(e ->
                e.getValue().phoneHash().equals(phoneHash) && e.getValue().purpose() == purpose);

        String challengeId = UUID.randomUUID().toString();
        String code = cryptoHashService.randomSixDigitOtp();
        String codeHash = cryptoHashService.hmacSha256(challengeId + ":" + code);
        Instant expiresAt = Instant.now().plusSeconds(properties.getSecurity().getOtpTtlSeconds());

        challenges.put(challengeId, new OtpChallenge(
                challengeId, userId, phoneHash, purpose, codeHash, deviceHint, delegationId, actionClass, 0, expiresAt));
        return new CreatedChallenge(challengeId, code, expiresAt);
    }

    public OtpChallenge peekChallenge(String challengeId) {
        OtpChallenge ch = challenges.get(challengeId);
        if (ch == null || Instant.now().isAfter(ch.expiresAt())) {
            challenges.remove(challengeId);
            throw new AuthApiException(HttpStatus.GONE, "CHALLENGE_EXPIRED",
                    "This code is no longer valid. Request a new one");
        }
        return ch;
    }

    public OtpChallenge verifyAndConsume(String challengeId, String otp) {
        OtpChallenge ch = peekChallenge(challengeId);
        String actualHash = cryptoHashService.hmacSha256(challengeId + ":" + otp);
        if (!cryptoHashService.constantTimeEquals(ch.codeHash(), actualHash)) {
            int nextAttempts = ch.attempts() + 1;
            int max = properties.getSecurity().getOtpMaxAttempts();
            if (nextAttempts >= max) {
                challenges.remove(challengeId);
                throw new AuthApiException(HttpStatus.GONE, "CHALLENGE_EXPIRED",
                        "This code is no longer valid. Request a new one");
            }
            challenges.put(challengeId, new OtpChallenge(
                    ch.challengeId(), ch.userId(), ch.phoneHash(), ch.purpose(),
                    ch.codeHash(), ch.deviceHint(), ch.delegationId(), ch.actionClass(),
                    nextAttempts, ch.expiresAt()));
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "Incorrect code",
                    Map.of("attemptsRemaining", max - nextAttempts));
        }
        challenges.remove(challengeId);
        return ch;
    }

    public String issueStepUpToken(UUID userId, StepUpActionClass actionClass) {
        String token = cryptoHashService.randomOpaqueToken("su_");
        Instant exp = Instant.now().plusSeconds(properties.getSecurity().getStepUpTtlSeconds());
        stepUpTokens.put(token, new StepUpTokenRecord(token, userId, actionClass, exp));
        return token;
    }

    public void consumeStepUpToken(String token, UUID expectedUserId, StepUpActionClass expectedAction) {
        if (token == null || token.isBlank()) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "STEP_UP_REQUIRED",
                    "Please confirm with a new code", Map.of("stepUp", true));
        }
        StepUpTokenRecord rec = stepUpTokens.remove(token);
        if (rec == null || Instant.now().isAfter(rec.expiresAt())
                || !rec.userId().equals(expectedUserId)
                || rec.actionClass() != expectedAction) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "STEP_UP_REQUIRED",
                    "Please confirm with a new code", Map.of("stepUp", true));
        }
    }

    public void clearAll() {
        challenges.clear();
        stepUpTokens.clear();
    }

    public record CreatedChallenge(String challengeId, String rawOtp, Instant expiresAt) {}

    public record OtpChallenge(
            String challengeId,
            UUID userId,
            String phoneHash,
            OtpPurpose purpose,
            String codeHash,
            String deviceHint,
            UUID delegationId,
            StepUpActionClass actionClass,
            int attempts,
            Instant expiresAt) {}

    public record StepUpTokenRecord(String token, UUID userId, StepUpActionClass actionClass, Instant expiresAt) {}
}
