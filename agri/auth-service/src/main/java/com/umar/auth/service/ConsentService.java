package com.umar.auth.service;

import com.umar.auth.client.DataVaultClient;
import com.umar.auth.client.HookSdkClient;
import com.umar.auth.config.AuthProperties;
import com.umar.auth.domain.entity.ConsentRecordEntity;
import com.umar.auth.domain.entity.UserAccountEntity;
import com.umar.auth.exception.AuthApiException;
import com.umar.auth.repository.ConsentRecordRepository;
import com.umar.auth.repository.UserAccountRepository;
import com.umar.auth.security.AuthIdentity;
import com.umar.auth.security.AuthSecurityContext;
import com.umar.auth.service.support.OtpChallengeService;
import com.umar.auth.service.support.OutboxService;
import com.umar.auth.service.support.RateLimitService;
import com.umar.enums.auth.*;
import com.umar.payload.request.auth.RecordConsentRequest;
import com.umar.payload.request.auth.WithdrawConsentRequest;
import com.umar.payload.response.auth.RecordConsentResponse;
import com.umar.payload.response.auth.WithdrawConsentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private static final Map<ConsentPurpose, String> CURRENT_VERSIONS = Map.of(
            ConsentPurpose.DATA_COLLECTION, "3",
            ConsentPurpose.SATELLITE_MONITORING, "3",
            ConsentPurpose.AUTOMATED_PAYOUT, "3",
            ConsentPurpose.PARTNER_DATA_SHARING, "1"
    );

    private static final List<ConsentPurpose> CORE_PURPOSES = List.of(
            ConsentPurpose.DATA_COLLECTION,
            ConsentPurpose.SATELLITE_MONITORING,
            ConsentPurpose.AUTOMATED_PAYOUT
    );

    private final ConsentRecordRepository consentRepository;
    private final UserAccountRepository userAccountRepository;
    private final DelegationService delegationService;
    private final DataVaultClient dataVaultClient;
    private final HookSdkClient hookSdkClient;
    private final OtpChallengeService otpChallengeService;
    private final OutboxService outboxService;
    private final RateLimitService rateLimitService;
    private final IdentityContextService identityContextService;
    private final AuthProperties properties;

    @Transactional
    public RecordResult recordConsent(String delegationHeader, RecordConsentRequest request) {
        AuthIdentity actor = AuthSecurityContext.require();
        UUID farmerUserId = resolveFarmerSubject(actor, delegationHeader, DelegationScope.CONSENT_CAPTURE);

        rateLimitService.checkAndIncrement("consent:" + farmerUserId, 30, 3600);

        UserAccountEntity farmer = userAccountRepository.findById(farmerUserId)
                .orElseThrow(() -> new AuthApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "Account is not active"));
        if (farmer.getStatus() != AccountStatus.ACTIVE) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "Account is not active");
        }

        if (!properties.getSecurity().getSupportedLanguages().contains(request.getLanguage().toLowerCase())) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported language");
        }

        for (RecordConsentRequest.ConsentItem item : request.getConsents()) {
            String expected = CURRENT_VERSIONS.get(item.getPurpose());
            if (!expected.equals(item.getTextVersion())) {
                throw new AuthApiException(HttpStatus.CONFLICT, "CONSENT_VERSION_OUTDATED",
                        "The consent text has changed", Map.of("currentVersion", expected));
            }
            if (item.getPurpose() == ConsentPurpose.PARTNER_DATA_SHARING
                    && (item.getPartnerId() == null || item.getPartnerId().isBlank())) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "partnerId is required for PARTNER_DATA_SHARING");
            }
        }

        verifyEvidence(farmerUserId, request.getEvidence());

        Instant now = Instant.now();
        List<RecordConsentResponse.ConsentEntry> entries = new ArrayList<>();
        boolean anyCreated = false;

        for (RecordConsentRequest.ConsentItem item : request.getConsents()) {
            Optional<ConsentRecordEntity> existing = consentRepository
                    .findFirstByUserIdAndPurposeAndTextVersionAndStatusOrderByGrantedAtDesc(
                            farmerUserId, item.getPurpose(), item.getTextVersion(), ConsentStatus.ACTIVE);
            if (existing.isPresent()) {
                ConsentRecordEntity rec = existing.get();
                entries.add(toEntry(rec));
                continue;
            }
            anyCreated = true;
            ConsentRecordEntity saved = consentRepository.save(ConsentRecordEntity.builder()
                    .consentId(UUID.randomUUID())
                    .userId(farmerUserId)
                    .purpose(item.getPurpose())
                    .textVersion(item.getTextVersion())
                    .language(request.getLanguage().toLowerCase())
                    .channel(request.getChannel())
                    .evidenceType(request.getEvidence().getType())
                    .evidenceRef(request.getEvidence().getReference())
                    .partnerId(item.getPartnerId())
                    .capturedBy(actor.userId() != null ? actor.userId() : farmerUserId)
                    .status(ConsentStatus.ACTIVE)
                    .grantedAt(now)
                    .build());
            entries.add(toEntry(saved));

            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", "consent.recorded");
            evt.put("consentId", saved.getConsentId().toString());
            evt.put("userId", farmerUserId.toString());
            evt.put("purpose", saved.getPurpose().name());
            evt.put("textVersion", saved.getTextVersion());
            evt.put("language", saved.getLanguage());
            evt.put("channel", saved.getChannel().name());
            evt.put("capturedBy", saved.getCapturedBy().toString());
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getIdentityTopic(), "consent.recorded",
                    farmerUserId.toString(), evt);

            hookSdkClient.postConsentRecord(saved.getConsentId().toString(),
                    farmerUserId.toString(), saved.getPurpose().name(), "GRANTED");
        }

        identityContextService.evict(farmerUserId);

        List<String> missingCore = computeMissingCore(farmerUserId);
        String nextStep = !missingCore.isEmpty()
                ? "RECORD_CORE_CONSENTS"
                : (farmer.getKycTier() == KycTier.TIER_0 ? "SUBMIT_KYC" : "BUY_POLICY");

        RecordConsentResponse body = RecordConsentResponse.builder()
                .consents(entries)
                .missingCoreConsents(missingCore)
                .nextStep(nextStep)
                .build();
        return new RecordResult(anyCreated, body);
    }

    @Transactional
    public WithdrawConsentResponse withdrawConsent(
            UUID consentId,
            String delegationHeader,
            String stepUpToken,
            WithdrawConsentRequest request) {
        AuthIdentity actor = AuthSecurityContext.require();
        if (delegationHeader != null && !delegationHeader.isBlank()) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }

        boolean isSupport = actor.hasRole(AuthRole.SUPPORT);
        ConsentRecordEntity record = consentRepository.findById(consentId)
                .orElseThrow(() -> new AuthApiException(HttpStatus.NOT_FOUND, "CONSENT_NOT_FOUND", "Consent not found"));

        if (!isSupport) {
            if (actor.userId() == null || !actor.userId().equals(record.getUserId())) {
                throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
            }
            otpChallengeService.consumeStepUpToken(stepUpToken, actor.userId(), StepUpActionClass.CONSENT_CHANGE);
        } else {
            // Support must provide a valid step-up token bound to the consent owner on verified request
            otpChallengeService.consumeStepUpToken(stepUpToken, record.getUserId(), StepUpActionClass.CONSENT_CHANGE);
        }

        if (record.getStatus() != ConsentStatus.ACTIVE) {
            throw new AuthApiException(HttpStatus.CONFLICT, "CONSENT_ALREADY_WITHDRAWN",
                    "This consent was already withdrawn");
        }

        rateLimitService.checkAndIncrement("withdraw:" + record.getUserId(), 10, 3600);

        Instant now = Instant.now();
        // Append-only: add a withdrawal ledger entry while marking current active row withdrawn timestamp
        record.setStatus(ConsentStatus.WITHDRAWN);
        record.setWithdrawalReason(request.getReason());
        record.setWithdrawnAt(now);
        consentRepository.save(record);

        List<String> effects = mapEffects(record.getPurpose());

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "consent.withdrawn");
        evt.put("consentId", record.getConsentId().toString());
        evt.put("userId", record.getUserId().toString());
        evt.put("purpose", record.getPurpose().name());
        evt.put("channel", request.getChannel().name());
        evt.put("withdrawnBy", actor.userId() != null ? actor.userId().toString() : actor.actorId());
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getIdentityTopic(), "consent.withdrawn",
                record.getUserId().toString(), evt);

        identityContextService.evict(record.getUserId());
        hookSdkClient.postConsentRecord(record.getConsentId().toString(),
                record.getUserId().toString(), record.getPurpose().name(), "WITHDRAWN");

        return WithdrawConsentResponse.builder()
                .consentId(record.getConsentId().toString())
                .purpose(record.getPurpose().name())
                .status(ConsentStatus.WITHDRAWN.name())
                .withdrawnAt(now.toString())
                .effects(effects)
                .build();
    }

    public boolean hasActiveConsent(UUID userId, ConsentPurpose purpose) {
        return consentRepository
                .findFirstByUserIdAndPurposeAndStatusOrderByGrantedAtDesc(userId, purpose, ConsentStatus.ACTIVE)
                .isPresent();
    }

    private UUID resolveFarmerSubject(AuthIdentity actor, String delegationHeader, DelegationScope scope) {
        if (delegationHeader != null && !delegationHeader.isBlank()) {
            UUID delegationId;
            try {
                delegationId = UUID.fromString(delegationHeader.trim());
            } catch (IllegalArgumentException e) {
                throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Not authorised to record consent for this user");
            }
            if (actor.userId() == null) {
                throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Not authorised to record consent for this user");
            }
            return delegationService.requireActiveDelegationForAgent(delegationId, actor.userId(), scope);
        }
        if (actor.userId() == null) {
            throw new AuthApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Missing or expired token");
        }
        return actor.userId();
    }

    private void verifyEvidence(UUID userId, RecordConsentRequest.ConsentEvidence evidence) {
        if (evidence.getType() == ConsentEvidenceType.VOICE_RECORDING_REF) {
            if (!dataVaultClient.verifyDocumentExists(evidence.getReference())) {
                throw new AuthApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVIDENCE_INVALID",
                        "Evidence could not be verified");
            }
        } else if (evidence.getType() == ConsentEvidenceType.OTP_CONFIRMATION) {
            if (evidence.getReference() == null || evidence.getReference().isBlank()) {
                throw new AuthApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVIDENCE_INVALID",
                        "Evidence could not be verified");
            }
        }
    }

    private List<String> computeMissingCore(UUID userId) {
        List<String> missing = new ArrayList<>();
        for (ConsentPurpose p : CORE_PURPOSES) {
            if (!hasActiveConsent(userId, p)) {
                missing.add(p.name());
            }
        }
        return missing;
    }

    private List<String> mapEffects(ConsentPurpose purpose) {
        return switch (purpose) {
            case AUTOMATED_PAYOUT -> List.of("PAYOUTS_REQUIRE_CONFIRMATION");
            case SATELLITE_MONITORING -> List.of("MONITORING_PAUSED", "POLICY_COVER_AT_RISK");
            case DATA_COLLECTION -> List.of("NEW_COLLECTION_STOPPED", "POLICY_FLAGGED");
            case PARTNER_DATA_SHARING -> List.of("PARTNER_SHARING_STOPPED");
        };
    }

    private RecordConsentResponse.ConsentEntry toEntry(ConsentRecordEntity rec) {
        return RecordConsentResponse.ConsentEntry.builder()
                .consentId(rec.getConsentId().toString())
                .purpose(rec.getPurpose().name())
                .textVersion(rec.getTextVersion())
                .status(rec.getStatus().name())
                .grantedAt(rec.getGrantedAt().toString())
                .build();
    }

    public record RecordResult(boolean createdAny, RecordConsentResponse response) {}
}
