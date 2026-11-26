package com.umar.auth.service;

import com.umar.auth.client.DataVaultClient;
import com.umar.auth.client.HookSdkClient;
import com.umar.auth.client.KycVerificationProviderClient;
import com.umar.auth.config.AuthProperties;
import com.umar.auth.domain.entity.KycCaseEntity;
import com.umar.auth.domain.entity.UserAccountEntity;
import com.umar.auth.exception.AuthApiException;
import com.umar.auth.repository.KycCaseRepository;
import com.umar.auth.repository.UserAccountRepository;
import com.umar.auth.security.AuthIdentity;
import com.umar.auth.security.AuthSecurityContext;
import com.umar.auth.service.support.OutboxService;
import com.umar.auth.service.support.RateLimitService;
import com.umar.enums.auth.*;
import com.umar.payload.request.auth.KycDecisionRequest;
import com.umar.payload.request.auth.SubmitKycRequest;
import com.umar.payload.response.auth.KycDecisionResponse;
import com.umar.payload.response.auth.KycStatusResponse;
import com.umar.payload.response.auth.SubmitKycResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class KycService {

    private static final List<KycCaseStatus> OPEN_STATUSES = List.of(
            KycCaseStatus.SUBMITTED, KycCaseStatus.AUTO_VERIFYING, KycCaseStatus.MANUAL_REVIEW);

    private final KycCaseRepository kycCaseRepository;
    private final UserAccountRepository userAccountRepository;
    private final ConsentService consentService;
    private final DelegationService delegationService;
    private final DataVaultClient dataVaultClient;
    private final KycVerificationProviderClient providerClient;
    private final HookSdkClient hookSdkClient;
    private final OutboxService outboxService;
    private final RateLimitService rateLimitService;
    private final IdentityContextService identityContextService;
    private final AuthProperties properties;

    @Transactional
    public SubmitKycResponse submitKyc(
            String delegationHeader,
            SubmitKycRequest request,
            MultipartFile documentFront,
            MultipartFile documentBack,
            MultipartFile selfie) {
        AuthIdentity actor = AuthSecurityContext.require();
        UUID farmerUserId;
        UUID delegationId = null;
        if (delegationHeader != null && !delegationHeader.isBlank()) {
            try {
                delegationId = UUID.fromString(delegationHeader.trim());
            } catch (IllegalArgumentException e) {
                throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Not authorised to submit for this user");
            }
            if (actor.userId() == null) {
                throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Not authorised to submit for this user");
            }
            farmerUserId = delegationService.requireActiveDelegationForAgent(
                    delegationId, actor.userId(), DelegationScope.KYC_SUBMIT);
        } else {
            if (actor.userId() == null) {
                throw new AuthApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Missing or expired token");
            }
            farmerUserId = actor.userId();
        }

        UserAccountEntity farmer = userAccountRepository.findById(farmerUserId)
                .orElseThrow(() -> new AuthApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "User not found"));

        if (Period.between(request.getDateOfBirth(), LocalDate.now()).getYears() < 18) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Accounts require age 18 or over");
        }

        if (!consentService.hasActiveConsent(farmerUserId, ConsentPurpose.DATA_COLLECTION)) {
            throw new AuthApiException(HttpStatus.PRECONDITION_FAILED, "CONSENT_REQUIRED",
                    "Consent is required before submitting documents");
        }

        KycTier targetTier = request.getTargetTier() != null ? request.getTargetTier() : KycTier.TIER_1;
        if (targetTier == KycTier.TIER_0) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "targetTier must be TIER_1 or TIER_2");
        }
        if (farmer.getKycTier().ordinal() >= targetTier.ordinal()) {
            throw new AuthApiException(HttpStatus.CONFLICT, "ALREADY_VERIFIED",
                    "Already verified at this level");
        }

        List<KycCaseEntity> openCases = kycCaseRepository.findByUserIdAndStatusIn(farmerUserId, OPEN_STATUSES);
        if (!openCases.isEmpty()) {
            throw new AuthApiException(HttpStatus.CONFLICT, "KYC_IN_PROGRESS",
                    "A verification is already in progress",
                    Map.of("kycId", openCases.getFirst().getKycId().toString()));
        }

        rateLimitService.checkAndIncrement("kyc:sub:" + farmerUserId,
                properties.getLimits().getKycSubmissionsPerUserPerDay(), 86400);

        validateUpload(documentFront, true);
        if (request.getIdType() == IdType.NATIONAL_ID || request.getIdType() == IdType.VOTER_ID) {
            validateUpload(documentBack, true);
        }
        validateUpload(selfie, true);

        String idHash = dataVaultClient.computeKeyedHash(
                request.getIdType().name() + ":" + request.getIdNumber().trim(), "ID_HASH");
        String idToken = dataVaultClient.tokenize("ID_NUMBER", request.getIdNumber().trim(),
                farmerUserId.toString(), "KYC_VERIFICATION");

        String frontToken = storeFile(farmerUserId, "front", documentFront);
        String backToken = documentBack != null && !documentBack.isEmpty()
                ? storeFile(farmerUserId, "back", documentBack) : null;
        String selfieToken = storeFile(farmerUserId, "selfie", selfie);

        Set<KycFlag> flags = EnumSet.noneOf(KycFlag.class);
        if (!kycCaseRepository.findByIdNumberHashAndUserIdNot(idHash, farmerUserId).isEmpty()) {
            flags.add(KycFlag.DUPLICATE_ID);
        }

        Instant now = Instant.now();
        KycCaseEntity kycCase = KycCaseEntity.builder()
                .kycId(UUID.randomUUID())
                .userId(farmerUserId)
                .idType(request.getIdType())
                .idNumberToken(idToken)
                .idNumberHash(idHash)
                .frontDocToken(frontToken)
                .backDocToken(backToken)
                .selfieDocToken(selfieToken)
                .targetTier(targetTier)
                .status(KycCaseStatus.SUBMITTED)
                .submittedBy(actor.userId() != null ? actor.userId() : farmerUserId)
                .delegationId(delegationId)
                .secondReviewPending(false)
                .submittedAt(now)
                .expiresAt(now.plus(properties.getKyc().getCaseExpiryDays(), ChronoUnit.DAYS))
                .build();
        kycCase.setFlags(flags);
        kycCaseRepository.save(kycCase);

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "kyc.submitted");
        evt.put("kycId", kycCase.getKycId().toString());
        evt.put("userId", farmerUserId.toString());
        evt.put("idType", kycCase.getIdType().name());
        evt.put("targetTier", targetTier.name());
        evt.put("flags", flags.stream().map(Enum::name).toList());
        evt.put("submittedBy", kycCase.getSubmittedBy().toString());
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getIdentityTopic(), "kyc.submitted",
                kycCase.getKycId().toString(), evt);

        // Run automatic verification step synchronously in-process for deterministic pipeline progression
        runAutoVerification(kycCase, farmer, request.getFullNameOnId(), request.getDateOfBirth().toString());

        return SubmitKycResponse.builder()
                .kycId(kycCase.getKycId().toString())
                .status(KycCaseStatus.SUBMITTED.name())
                .targetTier(targetTier.name())
                .submittedAt(now.toString())
                .estimatedDecisionBy(now.plus(15, ChronoUnit.MINUTES).toString())
                .build();
    }

    public KycStatusResponse getKycStatus(UUID kycId) {
        AuthIdentity actor = AuthSecurityContext.require();
        boolean isStaff = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.COMPLIANCE_OFFICER, AuthRole.KYC_REVIEWER, AuthRole.ADMIN);

        KycCaseEntity kycCase = kycCaseRepository.findById(kycId)
                .orElseThrow(() -> new AuthApiException(HttpStatus.NOT_FOUND, "KYC_NOT_FOUND",
                        "Verification case not found"));

        if (!isStaff) {
            boolean isOwner = actor.userId() != null && actor.userId().equals(kycCase.getUserId());
            boolean isDelegatedAgent = actor.userId() != null
                    && kycCase.getDelegationId() != null
                    && delegationService.validateDelegation(
                            kycCase.getDelegationId(), actor.userId(), kycCase.getUserId(), DelegationScope.KYC_SUBMIT)
                    .valid();
            if (!isOwner && !isDelegatedAgent) {
                throw new AuthApiException(HttpStatus.NOT_FOUND, "KYC_NOT_FOUND", "Verification case not found");
            }
        }

        UserAccountEntity user = userAccountRepository.findById(kycCase.getUserId())
                .orElseThrow(() -> new AuthApiException(HttpStatus.NOT_FOUND, "KYC_NOT_FOUND",
                        "Verification case not found"));

        String nextAction = switch (kycCase.getStatus()) {
            case SUBMITTED, AUTO_VERIFYING, MANUAL_REVIEW -> "WAIT";
            case VERIFIED -> "NONE";
            case REJECTED, EXPIRED -> "RESUBMIT";
        };

        KycStatusResponse.KycStatusResponseBuilder builder = KycStatusResponse.builder()
                .kycId(kycCase.getKycId().toString())
                .status(kycCase.getStatus().name())
                .targetTier(kycCase.getTargetTier().name())
                .currentTier(user.getKycTier().name())
                .reasonCodes(kycCase.getReasonCodes())
                .nextAction(nextAction)
                .estimatedDecisionBy(OPEN_STATUSES.contains(kycCase.getStatus())
                        ? kycCase.getSubmittedAt().plus(15, ChronoUnit.MINUTES).toString()
                        : null);

        if (isStaff) {
            builder.staff(KycStatusResponse.StaffView.builder()
                    .matchScore(kycCase.getMatchScore())
                    .providerRef(kycCase.getProviderRef())
                    .flags(kycCase.getFlags().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()))
                    .reviewerId(kycCase.getReviewerId())
                    .secondReviewerId(kycCase.getSecondReviewerId())
                    .queuePosition(kycCase.getStatus() == KycCaseStatus.MANUAL_REVIEW ? 1 : 0)
                    .build());
        }
        return builder.build();
    }

    @Transactional
    public KycDecisionResponse decideKycCase(UUID kycId, KycDecisionRequest request) {
        AuthIdentity actor = AuthSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.KYC_REVIEWER, AuthRole.ADMIN)) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        String reviewerId = actor.actorId();
        if (request.getNote() == null || request.getNote().trim().length() < 20) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "MISSING_JUSTIFICATION",
                    "A written justification is required");
        }

        KycCaseEntity kycCase = kycCaseRepository.findById(kycId)
                .orElseThrow(() -> new AuthApiException(HttpStatus.NOT_FOUND, "KYC_NOT_FOUND",
                        "Verification case not found"));

        if (kycCase.getStatus() != KycCaseStatus.MANUAL_REVIEW) {
            throw new AuthApiException(HttpStatus.CONFLICT, "CASE_NOT_IN_REVIEW",
                    "This case is not awaiting review");
        }

        Instant now = Instant.now();
        if (kycCase.getLockHolder() != null && kycCase.getLockedUntil() != null
                && now.isBefore(kycCase.getLockedUntil())
                && !kycCase.getLockHolder().equals(reviewerId)) {
            throw new AuthApiException(HttpStatus.CONFLICT, "CASE_LOCKED",
                    "This case is being reviewed by someone else");
        }

        if (actor.userId() != null && (actor.userId().equals(kycCase.getUserId())
                || actor.userId().equals(kycCase.getSubmittedBy()))) {
            throw new AuthApiException(HttpStatus.FORBIDDEN, "REVIEWER_CONFLICT", "You cannot decide this case");
        }

        UserAccountEntity user = userAccountRepository.findById(kycCase.getUserId())
                .orElseThrow(() -> new AuthApiException(HttpStatus.NOT_FOUND, "KYC_NOT_FOUND", "User not found"));

        if (request.getDecision() == KycDecisionType.APPROVE) {
            if (request.getApprovedTier() == null || request.getChecklist() == null
                    || !Boolean.TRUE.equals(request.getChecklist().getNameMatch())
                    || !Boolean.TRUE.equals(request.getChecklist().getPhotoMatch())
                    || !Boolean.TRUE.equals(request.getChecklist().getDocumentAuthentic())
                    || !Boolean.TRUE.equals(request.getChecklist().getDuplicateResolved())) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "approvedTier and full positive checklist are required for APPROVE");
            }
            if (request.getApprovedTier().ordinal() > kycCase.getTargetTier().ordinal()) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "approvedTier cannot exceed targetTier");
            }

            boolean requiresFourEyes = kycCase.getFlags().contains(KycFlag.DUPLICATE_ID)
                    || kycCase.getFlags().contains(KycFlag.HIGH_FRAUD_SCORE)
                    || kycCase.getTargetTier() == KycTier.TIER_2;

            if (requiresFourEyes && !kycCase.isSecondReviewPending()) {
                kycCase.setReviewerId(reviewerId);
                kycCase.setReviewerNote(request.getNote().trim());
                kycCase.setSecondReviewPending(true);
                kycCase.setLockHolder(null);
                kycCase.setLockedUntil(null);
                kycCaseRepository.save(kycCase);

                return KycDecisionResponse.builder()
                        .kycId(kycCase.getKycId().toString())
                        .status(KycCaseStatus.MANUAL_REVIEW.name())
                        .secondReviewPending(true)
                        .currentTier(user.getKycTier().name())
                        .decidedBy(reviewerId)
                        .build();
            }

            if (requiresFourEyes && kycCase.isSecondReviewPending()) {
                if (reviewerId.equals(kycCase.getReviewerId())) {
                    throw new AuthApiException(HttpStatus.CONFLICT, "SECOND_REVIEWER_REQUIRED",
                            "A different reviewer must confirm this decision");
                }
                kycCase.setSecondReviewerId(reviewerId);
                kycCase.setSecondReviewPending(false);
            } else {
                kycCase.setReviewerId(reviewerId);
            }

            kycCase.setReviewerNote(request.getNote().trim());
            kycCase.setStatus(KycCaseStatus.VERIFIED);
            kycCase.setDecidedAt(now);
            kycCase.setLockHolder(null);
            kycCase.setLockedUntil(null);
            kycCaseRepository.save(kycCase);

            if (request.getApprovedTier().ordinal() > user.getKycTier().ordinal()) {
                user.setKycTier(request.getApprovedTier());
                userAccountRepository.save(user);
            }
            identityContextService.evict(user.getUserId());

            publishKycVerified(kycCase, user, "MANUAL", reviewerId, kycCase.getSecondReviewerId(), now);
            hookSdkClient.postKycDecision(kycCase.getKycId().toString(), user.getUserId().toString(), "VERIFIED");

            return KycDecisionResponse.builder()
                    .kycId(kycCase.getKycId().toString())
                    .status(KycCaseStatus.VERIFIED.name())
                    .secondReviewPending(false)
                    .currentTier(user.getKycTier().name())
                    .decidedBy(reviewerId)
                    .decidedAt(now.toString())
                    .build();
        } else {
            if (request.getReasonCodes() == null || request.getReasonCodes().isEmpty()) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "reasonCodes are required for REJECT or REQUEST_RESUBMIT");
            }
            kycCase.setReviewerId(reviewerId);
            kycCase.setReviewerNote(request.getNote().trim());
            kycCase.setReasonCodes(request.getReasonCodes());
            kycCase.setStatus(KycCaseStatus.REJECTED);
            kycCase.setDecidedAt(now);
            kycCase.setSecondReviewPending(false);
            kycCase.setLockHolder(null);
            kycCase.setLockedUntil(null);
            kycCaseRepository.save(kycCase);

            publishKycRejected(kycCase, reviewerId, now);
            hookSdkClient.postKycDecision(kycCase.getKycId().toString(), user.getUserId().toString(), "REJECTED");

            return KycDecisionResponse.builder()
                    .kycId(kycCase.getKycId().toString())
                    .status(KycCaseStatus.REJECTED.name())
                    .secondReviewPending(false)
                    .currentTier(user.getKycTier().name())
                    .decidedBy(reviewerId)
                    .decidedAt(now.toString())
                    .build();
        }
    }

    @Transactional
    public void handleFraudFlag(UUID userId, String reason) {
        List<KycCaseEntity> openCases = kycCaseRepository.findByUserIdAndStatusIn(userId, OPEN_STATUSES);
        for (KycCaseEntity c : openCases) {
            Set<KycFlag> flags = c.getFlags();
            flags.add(KycFlag.HIGH_FRAUD_SCORE);
            c.setFlags(flags);
            if (c.getStatus() == KycCaseStatus.SUBMITTED || c.getStatus() == KycCaseStatus.AUTO_VERIFYING) {
                c.setStatus(KycCaseStatus.MANUAL_REVIEW);
            }
            kycCaseRepository.save(c);
        }
    }

    private void runAutoVerification(KycCaseEntity kycCase, UserAccountEntity user, String fullName, String dob) {
        KycVerificationProviderClient.VerificationOutcome outcome = providerClient.verify(
                kycCase.getKycId().toString(), kycCase.getIdType().name(), kycCase.getIdNumberToken(), fullName, dob);

        Instant now = Instant.now();
        if (!outcome.providerAvailable()) {
            kycCase.setStatus(KycCaseStatus.AUTO_VERIFYING);
            kycCaseRepository.save(kycCase);
            return;
        }

        kycCase.setMatchScore(outcome.score());
        kycCase.setProviderRef(outcome.providerRef());

        long recentRejections = kycCaseRepository.countByUserIdAndStatusAndDecidedAtAfter(
                user.getUserId(), KycCaseStatus.REJECTED, now.minus(30, ChronoUnit.DAYS));

        boolean hasFlags = !kycCase.getFlags().isEmpty() || recentRejections >= 3;
        if (outcome.score().compareTo(properties.getKyc().getAutoVerifyThreshold()) >= 0
                && outcome.allFieldsMatch() && !hasFlags && kycCase.getTargetTier() == KycTier.TIER_1) {
            kycCase.setStatus(KycCaseStatus.VERIFIED);
            kycCase.setDecidedAt(now);
            kycCaseRepository.save(kycCase);

            user.setKycTier(kycCase.getTargetTier());
            userAccountRepository.save(user);
            identityContextService.evict(user.getUserId());
            publishKycVerified(kycCase, user, "AUTO", "AUTO_PIPELINE", null, now);
        } else if (outcome.score().compareTo(properties.getKyc().getManualReviewThreshold()) >= 0 || hasFlags) {
            kycCase.setStatus(KycCaseStatus.MANUAL_REVIEW);
            kycCaseRepository.save(kycCase);
            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", "kyc.review.required");
            evt.put("kycId", kycCase.getKycId().toString());
            evt.put("userId", user.getUserId().toString());
            evt.put("flags", kycCase.getFlags().stream().map(Enum::name).toList());
            evt.put("priority", kycCase.getFlags().isEmpty() ? "NORMAL" : "HIGH");
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getIdentityTopic(), "kyc.review.required",
                    kycCase.getKycId().toString(), evt);
        } else {
            kycCase.setStatus(KycCaseStatus.REJECTED);
            kycCase.setReasonCodes(outcome.reasonCodes().isEmpty()
                    ? List.of("DOCUMENT_UNREADABLE") : outcome.reasonCodes());
            kycCase.setDecidedAt(now);
            kycCaseRepository.save(kycCase);
            publishKycRejected(kycCase, "AUTO_PIPELINE", now);
        }
    }

    private void validateUpload(MultipartFile file, boolean required) {
        if (file == null || file.isEmpty()) {
            if (required) {
                throw new AuthApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Required document file is missing");
            }
            return;
        }
        if (file.getSize() > properties.getKyc().getMaxFileBytes()) {
            throw new AuthApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File too large");
        }
        String ct = file.getContentType();
        if (ct != null && !ct.equalsIgnoreCase("image/jpeg") && !ct.equalsIgnoreCase("image/png")) {
            throw new AuthApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_QUALITY_LOW",
                    "Image quality too low", Map.of("hints", List.of("upload JPEG or PNG")));
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 32) {
                throw new AuthApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_QUALITY_LOW",
                        "Image quality too low", Map.of("hints", List.of("retake in good light")));
            }
        } catch (IOException e) {
            throw new AuthApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_QUALITY_LOW", "Image quality too low");
        }
    }

    private String storeFile(UUID userId, String part, MultipartFile file) {
        try {
            return dataVaultClient.storeDocument(
                    "KYC_DOCUMENT", userId.toString(), "KYC_VERIFICATION",
                    part + "-" + file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new AuthApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_QUALITY_LOW", "Could not read file");
        }
    }

    private void publishKycVerified(KycCaseEntity kycCase, UserAccountEntity user, String method,
                                    String decidedBy, String secondReviewer, Instant now) {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "kyc.verified");
        evt.put("kycId", kycCase.getKycId().toString());
        evt.put("userId", user.getUserId().toString());
        evt.put("tier", user.getKycTier().name());
        evt.put("method", method);
        evt.put("decidedBy", decidedBy);
        if (secondReviewer != null) evt.put("secondReviewer", secondReviewer);
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getIdentityTopic(), "kyc.verified",
                user.getUserId().toString(), evt);
    }

    private void publishKycRejected(KycCaseEntity kycCase, String decidedBy, Instant now) {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "kyc.rejected");
        evt.put("kycId", kycCase.getKycId().toString());
        evt.put("userId", kycCase.getUserId().toString());
        evt.put("reasonCodes", kycCase.getReasonCodes());
        evt.put("decidedBy", decidedBy);
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getIdentityTopic(), "kyc.rejected",
                kycCase.getUserId().toString(), evt);
    }
}
