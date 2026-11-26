package com.umar.farmer.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.KycTier;
import com.umar.enums.farmer.*;
import com.umar.farmer.client.AuthKycClient;
import com.umar.farmer.client.DataVaultClient;
import com.umar.farmer.client.HookSdkClient;
import com.umar.farmer.client.WalletProviderClient;
import com.umar.farmer.config.FarmerProperties;
import com.umar.farmer.domain.entity.FarmerProfileEntity;
import com.umar.farmer.domain.entity.FarmerWalletEntity;
import com.umar.farmer.exception.FarmerApiException;
import com.umar.farmer.repository.FarmerProfileRepository;
import com.umar.farmer.repository.FarmerWalletRepository;
import com.umar.farmer.repository.GroupMembershipRepository;
import com.umar.farmer.security.FarmerIdentity;
import com.umar.farmer.security.FarmerSecurityContext;
import com.umar.farmer.service.support.FarmerIdempotencyService;
import com.umar.farmer.service.support.FarmerOutboxService;
import com.umar.payload.request.farmer.RegisterWalletRequest;
import com.umar.payload.request.farmer.ReviewWalletRequest;
import com.umar.payload.response.farmer.ReviewWalletResponse;
import com.umar.payload.response.farmer.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final FarmerProfileRepository profileRepository;
    private final FarmerWalletRepository walletRepository;
    private final GroupMembershipRepository membershipRepository;
    private final AuthKycClient authKycClient;
    private final DataVaultClient dataVaultClient;
    private final WalletProviderClient walletProviderClient;
    private final HookSdkClient hookSdkClient;
    private final FarmerOutboxService outboxService;
    private final FarmerIdempotencyService idempotencyService;
    private final FarmerProperties properties;

    @Transactional(noRollbackFor = FarmerApiException.class)
    public WalletResponse registerOrChangeWallet(
            UUID farmerId,
            String delegationHeader,
            String stepUpToken,
            String idempotencyKey,
            RegisterWalletRequest request) {
        AuthIdentityCheck.requireOwnerOnly(delegationHeader);
        FarmerIdentity actor = FarmerSecurityContext.require();

        FarmerProfileEntity profile = profileRepository.findById(farmerId)
                .orElseThrow(() -> new FarmerApiException(HttpStatus.NOT_FOUND, "FARMER_NOT_FOUND", "Farmer not found"));
        if (actor.userId() == null || !actor.userId().equals(profile.getUserId())) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only the account holder can change wallets");
        }

        authKycClient.verifyStepUpToken(stepUpToken, profile.getUserId(), "WALLET_CHANGE");

        AuthKycClient.IdentitySnapshot idSnap = authKycClient.getIdentityContext(profile.getUserId());
        if (idSnap.kycTier().ordinal() < KycTier.TIER_1.ordinal()) {
            throw new FarmerApiException(HttpStatus.PRECONDITION_FAILED, "KYC_REQUIRED",
                    "Identity verification is required first");
        }

        Optional<WalletResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "FAR-04:" + farmerId, request, WalletResponse.class);
        if (replay.isPresent()) return replay.get();

        WalletResponse resp = registerWalletInternal(profile, request, false);
        idempotencyService.save(idempotencyKey, "FAR-04:" + farmerId, request, 201, resp);
        return resp;
    }

    public WalletResponse registerWalletInternal(
            FarmerProfileEntity profile,
            RegisterWalletRequest request,
            boolean isNewProfileCreation) {

        if (!properties.getWallet().getSupportedProviders().contains(request.getProvider().toUpperCase())) {
            throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Unsupported wallet provider: " + request.getProvider());
        }

        long activeOrPending = walletRepository.countByFarmerIdAndStatusNot(profile.getFarmerId(), WalletStatus.RETIRED);
        if (activeOrPending >= properties.getWallet().getMaxPerFarmer()) {
            throw new FarmerApiException(HttpStatus.UNPROCESSABLE_ENTITY, "WALLET_LIMIT_REACHED",
                    "Remove a wallet before adding another");
        }

        Instant dayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        if (walletRepository.countByFarmerIdAndCreatedAtAfter(profile.getFarmerId(), dayAgo)
                >= properties.getWallet().getMaxAttemptsPerDay()) {
            throw new FarmerApiException(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_ATTEMPTS_EXCEEDED",
                    "Too many attempts. Try again later", Map.of("retryAfterSeconds", 3600));
        }

        String walletHash = dataVaultClient.hash(request.getWalletNumber().trim(), "WALLET_LOOKUP");
        Optional<FarmerWalletEntity> duplicateOwn = walletRepository
                .findByFarmerIdAndWalletNumberHashAndStatusNot(profile.getFarmerId(), walletHash, WalletStatus.RETIRED);
        if (duplicateOwn.isPresent()) {
            throw new FarmerApiException(HttpStatus.CONFLICT, "WALLET_ALREADY_REGISTERED",
                    "Wallet already added", Map.of("walletId", duplicateOwn.get().getWalletId().toString()));
        }

        HookSdkClient.WalletHookDecision hookDecision = hookSdkClient.preWalletChange(
                profile.getFarmerId().toString(), request.getProvider());
        if (hookDecision == HookSdkClient.WalletHookDecision.REJECT) {
            throw new FarmerApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Wallet rejected by policy");
        }

        String walletToken = dataVaultClient.tokenize("WALLET_NUMBER", request.getWalletNumber().trim(),
                profile.getFarmerId().toString(), "WALLET_REGISTRATION");
        String masked = maskWallet(request.getWalletNumber().trim());

        Set<WalletFlag> flags = EnumSet.noneOf(WalletFlag.class);
        if (hookDecision == HookSdkClient.WalletHookDecision.REQUIRE_REVIEW) {
            flags.add(WalletFlag.HOOK_REVIEW);
        }
        boolean sharedAcrossFarmers = !walletRepository
                .findByWalletNumberHashAndFarmerIdNotAndStatusNotIn(
                        walletHash, profile.getFarmerId(), List.of(WalletStatus.REJECTED, WalletStatus.RETIRED))
                .isEmpty();
        if (sharedAcrossFarmers) {
            flags.add(WalletFlag.SHARED_WALLET);
        }

        Optional<String> providerHolderOpt = walletProviderClient.lookupAccountHolderName(
                request.getProvider(), request.getWalletNumber().trim());

        WalletStatus status;
        MatchBand matchBand = null;
        String providerNameToken = null;
        String reasonCode = null;

        if (providerHolderOpt.isEmpty()) {
            flags.add(WalletFlag.PROVIDER_OUTAGE);
            status = WalletStatus.PENDING_VERIFICATION;
        } else {
            String providerName = providerHolderOpt.get();
            providerNameToken = dataVaultClient.tokenize("LEGAL_NAME", providerName,
                    profile.getFarmerId().toString(), "WALLET_NAME_MATCH");
            String kycLegalName = dataVaultClient.detokenize(profile.getLegalNameToken(), "WALLET_NAME_MATCH");
            BigDecimal score = computeNameSimilarity(kycLegalName, providerName);

            if (score.compareTo(properties.getWallet().getNameMatchVerifiedThreshold()) >= 0) {
                matchBand = MatchBand.MATCHED;
                status = (flags.contains(WalletFlag.SHARED_WALLET) || flags.contains(WalletFlag.HOOK_REVIEW))
                        ? WalletStatus.PENDING_REVIEW : WalletStatus.VERIFIED;
            } else if (score.compareTo(properties.getWallet().getNameMatchReviewThreshold()) >= 0) {
                matchBand = MatchBand.REVIEW;
                status = WalletStatus.PENDING_REVIEW;
            } else {
                matchBand = MatchBand.FAILED;
                status = WalletStatus.REJECTED;
                reasonCode = "NAME_MISMATCH";
            }
        }

        List<FarmerWalletEntity> existingWallets = walletRepository.findByFarmerIdOrderByCreatedAtAsc(profile.getFarmerId());
        boolean hasExistingVerifiedPrimary = existingWallets.stream()
                .anyMatch(w -> w.isPrimary() && w.getStatus() == WalletStatus.VERIFIED);
        boolean wantPrimary = Boolean.TRUE.equals(request.getMakePrimary()) || (!hasExistingVerifiedPrimary && existingWallets.isEmpty());

        Instant now = Instant.now();
        Instant effectiveFrom = now;
        if (wantPrimary && hasExistingVerifiedPrimary && status == WalletStatus.VERIFIED) {
            int coolingHours = properties.getWallet().getPrimaryCoolingOffHours();
            effectiveFrom = now.plus(coolingHours, ChronoUnit.HOURS);
        }

        FarmerWalletEntity saved = walletRepository.save(FarmerWalletEntity.builder()
                .walletId(UUID.randomUUID())
                .farmerId(profile.getFarmerId())
                .provider(request.getProvider().toUpperCase())
                .walletNumberToken(walletToken)
                .walletNumberHash(walletHash)
                .maskedNumber(masked)
                .providerNameToken(providerNameToken)
                .matchBand(matchBand)
                .status(status)
                .primary(wantPrimary && status != WalletStatus.REJECTED)
                .effectiveFrom(effectiveFrom)
                .reasonCode(reasonCode)
                .createdAt(now)
                .build());
        saved.setFlags(flags);
        walletRepository.save(saved);

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "farmer.wallet.changed");
        evt.put("farmerId", profile.getFarmerId().toString());
        evt.put("walletId", saved.getWalletId().toString());
        evt.put("provider", saved.getProvider());
        evt.put("status", saved.getStatus().name());
        evt.put("isPrimary", saved.isPrimary());
        evt.put("effectiveFrom", effectiveFrom.toString());
        evt.put("flags", flags.stream().map(Enum::name).toList());
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getFarmerTopic(), "farmer.wallet.changed",
                profile.getFarmerId().toString(), evt);

        if (status == WalletStatus.PENDING_REVIEW) {
            Map<String, Object> revEvt = new LinkedHashMap<>();
            revEvt.put("eventType", "farmer.wallet.review.required");
            revEvt.put("farmerId", profile.getFarmerId().toString());
            revEvt.put("walletId", saved.getWalletId().toString());
            revEvt.put("flags", flags.stream().map(Enum::name).toList());
            revEvt.put("priority", flags.contains(WalletFlag.SHARED_WALLET) ? "HIGH" : "NORMAL");
            revEvt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getFarmerTopic(), "farmer.wallet.review.required",
                    saved.getWalletId().toString(), revEvt);
        }

        String nextAction = switch (status) {
            case VERIFIED -> effectiveFrom.isAfter(now) ? "WAIT" : "NONE";
            case PENDING_VERIFICATION, PENDING_REVIEW -> "WAIT";
            case REJECTED -> "USE_OWN_WALLET";
            default -> "CONTACT_SUPPORT";
        };

        return WalletResponse.builder()
                .walletId(saved.getWalletId().toString())
                .provider(saved.getProvider())
                .maskedNumber(saved.getMaskedNumber())
                .status(saved.getStatus().name())
                .isPrimary(saved.isPrimary())
                .effectiveFrom(saved.getEffectiveFrom().toString())
                .coolingUntil(effectiveFrom.isAfter(now) ? effectiveFrom.toString() : null)
                .reasonCode(reasonCode)
                .nextAction(nextAction)
                .build();
    }

    @Transactional
    public ReviewWalletResponse reviewWallet(UUID farmerId, UUID walletId, ReviewWalletRequest request) {
        FarmerIdentity actor = FarmerSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (request.getNote() == null || request.getNote().trim().length() < 20) {
            throw new FarmerApiException(HttpStatus.BAD_REQUEST, "MISSING_JUSTIFICATION",
                    "A written justification is required");
        }

        FarmerProfileEntity profile = profileRepository.findById(farmerId)
                .orElseThrow(() -> new FarmerApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
        FarmerWalletEntity wallet = walletRepository.findByWalletIdAndFarmerId(walletId, farmerId)
                .orElseThrow(() -> new FarmerApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));

        if (wallet.getStatus() != WalletStatus.PENDING_REVIEW && wallet.getStatus() != WalletStatus.PENDING_SECOND_REVIEW) {
            throw new FarmerApiException(HttpStatus.CONFLICT, "WALLET_NOT_IN_REVIEW",
                    "This wallet is not awaiting review");
        }

        if (actor.userId() != null && (actor.userId().equals(profile.getUserId())
                || actor.userId().equals(profile.getEnrolledByAgentId()))) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "REVIEWER_CONFLICT",
                    "You cannot decide this case");
        }

        Instant now = Instant.now();
        String reviewerId = actor.actorId();
        boolean requiresFourEyes = wallet.getFlags().contains(WalletFlag.SHARED_WALLET)
                || wallet.getFlags().contains(WalletFlag.HOOK_REVIEW);

        if (request.getDecision() == WalletReviewDecision.APPROVE) {
            if (request.getEvidence() == null || request.getEvidence().isEmpty()) {
                throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "evidence is required for APPROVE");
            }
            if (requiresFourEyes && wallet.getStatus() == WalletStatus.PENDING_REVIEW) {
                wallet.setStatus(WalletStatus.PENDING_SECOND_REVIEW);
                wallet.setReviewerId(reviewerId);
                wallet.setReviewerNote(request.getNote().trim());
                walletRepository.save(wallet);
                return ReviewWalletResponse.builder()
                        .walletId(walletId.toString())
                        .status(WalletStatus.PENDING_REVIEW.name())
                        .secondReviewPending(true)
                        .decidedBy(reviewerId)
                        .build();
            }
            if (wallet.getStatus() == WalletStatus.PENDING_SECOND_REVIEW) {
                if (reviewerId.equals(wallet.getReviewerId())) {
                    throw new FarmerApiException(HttpStatus.CONFLICT, "SECOND_REVIEWER_REQUIRED",
                            "A different reviewer must confirm this decision");
                }
                wallet.setSecondReviewerId(reviewerId);
            } else {
                wallet.setReviewerId(reviewerId);
            }

            wallet.setStatus(WalletStatus.VERIFIED);
            wallet.setReviewerNote(request.getNote().trim());
            boolean hasOtherPrimary = walletRepository.findByFarmerIdOrderByCreatedAtAsc(farmerId).stream()
                    .anyMatch(w -> !w.getWalletId().equals(walletId) && w.isPrimary() && w.getStatus() == WalletStatus.VERIFIED);
            Instant effectiveFrom = (wallet.isPrimary() && hasOtherPrimary)
                    ? now.plus(properties.getWallet().getPrimaryCoolingOffHours(), ChronoUnit.HOURS)
                    : now;
            wallet.setEffectiveFrom(effectiveFrom);
            walletRepository.save(wallet);

            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", "farmer.wallet.changed");
            evt.put("farmerId", farmerId.toString());
            evt.put("walletId", walletId.toString());
            evt.put("status", WalletStatus.VERIFIED.name());
            evt.put("isPrimary", wallet.isPrimary());
            evt.put("effectiveFrom", effectiveFrom.toString());
            evt.put("decidedBy", reviewerId);
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getFarmerTopic(), "farmer.wallet.changed",
                    farmerId.toString(), evt);

            return ReviewWalletResponse.builder()
                    .walletId(walletId.toString())
                    .status(WalletStatus.VERIFIED.name())
                    .secondReviewPending(false)
                    .effectiveFrom(effectiveFrom.toString())
                    .decidedBy(reviewerId)
                    .decidedAt(now.toString())
                    .build();
        } else {
            if (request.getReasonCode() == null || request.getReasonCode().isBlank()) {
                throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "reasonCode is required for REJECT");
            }
            wallet.setStatus(WalletStatus.REJECTED);
            wallet.setReasonCode(request.getReasonCode());
            wallet.setReviewerId(reviewerId);
            wallet.setReviewerNote(request.getNote().trim());
            walletRepository.save(wallet);

            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", "farmer.wallet.changed");
            evt.put("farmerId", farmerId.toString());
            evt.put("walletId", walletId.toString());
            evt.put("status", WalletStatus.REJECTED.name());
            evt.put("reasonCode", request.getReasonCode());
            evt.put("decidedBy", reviewerId);
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getFarmerTopic(), "farmer.wallet.changed",
                    farmerId.toString(), evt);

            return ReviewWalletResponse.builder()
                    .walletId(walletId.toString())
                    .status(WalletStatus.REJECTED.name())
                    .secondReviewPending(false)
                    .decidedBy(reviewerId)
                    .decidedAt(now.toString())
                    .build();
        }
    }

    public static BigDecimal computeNameSimilarity(String nameA, String nameB) {
        Set<String> tokensA = normalizeNameTokens(nameA);
        Set<String> tokensB = normalizeNameTokens(nameB);
        if (tokensA.isEmpty() || tokensB.isEmpty()) return BigDecimal.ZERO;
        if (tokensA.equals(tokensB)) return BigDecimal.ONE;

        // Initial match support (e.g. "A. ODHIAMBO" vs "AMINA ODHIAMBO")
        int matched = 0;
        boolean initialUsed = false;
        for (String tA : tokensA) {
            if (tokensB.contains(tA)) {
                matched++;
            } else {
                for (String tB : tokensB) {
                    if ((tA.length() == 1 && tB.startsWith(tA)) || (tB.length() == 1 && tA.startsWith(tB))) {
                        matched++;
                        initialUsed = true;
                        break;
                    }
                }
            }
        }
        double denom = Math.max(tokensA.size(), tokensB.size());
        double ratio = matched / denom;
        if (initialUsed && ratio >= 0.99) {
            return new BigDecimal("0.82"); // Initial match lands in REVIEW band (0.70 - 0.89)
        }
        return BigDecimal.valueOf(ratio);
    }

    private static Set<String> normalizeNameTokens(String raw) {
        if (raw == null) return Set.of();
        String norm = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9\\s]", " ")
                .toUpperCase()
                .trim();
        Set<String> titles = Set.of("MR", "MRS", "MS", "DR", "PROF");
        Set<String> result = new LinkedHashSet<>();
        for (String tok : norm.split("\\s+")) {
            if (!tok.isEmpty() && !titles.contains(tok)) {
                result.add(tok);
            }
        }
        return result;
    }

    private static String maskWallet(String num) {
        if (num.length() <= 4) return "****";
        return num.substring(0, Math.min(5, num.length() - 3)) + "*****" + num.substring(num.length() - 3);
    }

    private static class AuthIdentityCheck {
        static void requireOwnerOnly(String delegationHeader) {
            if (delegationHeader != null && !delegationHeader.isBlank()) {
                throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Only the account holder can change wallets");
            }
        }
    }
}
