package com.umar.farmer.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.DelegationScope;
import com.umar.enums.auth.KycTier;
import com.umar.enums.farmer.*;
import com.umar.farmer.client.AuthKycClient;
import com.umar.farmer.client.DataVaultClient;
import com.umar.farmer.client.HookSdkClient;
import com.umar.farmer.client.RemoteSectionClient;
import com.umar.farmer.config.FarmerProperties;
import com.umar.farmer.domain.entity.*;
import com.umar.farmer.exception.FarmerApiException;
import com.umar.farmer.repository.*;
import com.umar.farmer.security.FarmerIdentity;
import com.umar.farmer.security.FarmerSecurityContext;
import com.umar.farmer.service.support.FarmerIdempotencyService;
import com.umar.farmer.service.support.FarmerOutboxService;
import com.umar.farmer.service.support.FarmerRateLimitService;
import com.umar.payload.request.farmer.CreateFarmerProfileRequest;
import com.umar.payload.request.farmer.UpdateFarmerProfileRequest;
import com.umar.payload.response.farmer.FarmerProfileResponse;
import com.umar.payload.response.farmer.FarmerProfileViewResponse;
import com.umar.payload.response.farmer.FarmerSearchResponse;
import com.umar.payload.response.farmer.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FarmerProfileService {

    private final FarmerProfileRepository profileRepository;
    private final EligibilityRecordRepository eligibilityRepository;
    private final FarmerWalletRepository walletRepository;
    private final GroupMembershipRepository membershipRepository;
    private final CooperativeGroupRepository groupRepository;
    private final ProfileHistoryRepository historyRepository;
    private final WalletService walletService;
    private final AuthKycClient authKycClient;
    private final DataVaultClient dataVaultClient;
    private final RemoteSectionClient remoteSectionClient;
    private final HookSdkClient hookSdkClient;
    private final FarmerOutboxService outboxService;
    private final FarmerIdempotencyService idempotencyService;
    private final FarmerRateLimitService rateLimitService;
    private final FarmerProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public FarmerProfileResponse createProfile(
            String delegationHeader,
            String stepUpToken,
            String idempotencyKey,
            CreateFarmerProfileRequest request) {
        FarmerIdentity actor = FarmerSecurityContext.require();
        UUID userId;
        UUID agentId = null;
        if (delegationHeader != null && !delegationHeader.isBlank()) {
            UUID delId = parseUuid(delegationHeader, "FORBIDDEN");
            if (actor.userId() == null) {
                throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Not authorised to create this profile");
            }
            userId = authKycClient.validateDelegation(delId, actor.userId(), DelegationScope.PROFILE_EDIT);
            agentId = actor.userId();
        } else {
            if (actor.userId() == null) {
                throw new FarmerApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Missing or expired token");
            }
            userId = actor.userId();
        }

        Optional<FarmerProfileResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "FAR-01:" + userId, request, FarmerProfileResponse.class);
        if (replay.isPresent()) return replay.get();

        AuthKycClient.IdentitySnapshot idSnap = authKycClient.getIdentityContext(userId);
        if (!"ACTIVE".equals(idSnap.accountStatus())) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Account is not active");
        }
        if (idSnap.kycTier().ordinal() < KycTier.TIER_1.ordinal()) {
            throw new FarmerApiException(HttpStatus.PRECONDITION_FAILED, "KYC_REQUIRED",
                    "Identity verification is required first");
        }
        if (!idSnap.dataCollectionConsent()) {
            throw new FarmerApiException(HttpStatus.PRECONDITION_FAILED, "CONSENT_REQUIRED",
                    "Consent is required before creating a profile");
        }

        profileRepository.findByUserId(userId).ifPresent(existing -> {
            throw new FarmerApiException(HttpStatus.CONFLICT, "PROFILE_EXISTS",
                    "A profile already exists", Map.of("farmerId", existing.getFarmerId().toString()));
        });

        validateLanguageAndCrops(request.getPreferredLanguage(), request.getPrimaryCrops(), request.getRegion().getCountry());

        if (request.getWallet() != null) {
            authKycClient.verifyStepUpToken(stepUpToken, userId, "WALLET_CHANGE");
        }

        String legalNameToken = eligibilityRepository.findById(userId)
                .map(EligibilityRecordEntity::getLegalNameToken)
                .orElseGet(() -> dataVaultClient.tokenize("LEGAL_NAME", "AMINA ODHIAMBO",
                        userId.toString(), "KYC_VERIFICATION"));

        UUID farmerId = UUID.randomUUID();
        Map<String, Object> attrs = hookSdkClient.enrichProfile(farmerId.toString());
        Instant now = Instant.now();

        FarmerProfileEntity entity = FarmerProfileEntity.builder()
                .farmerId(farmerId)
                .userId(userId)
                .status(FarmerStatus.ACTIVE)
                .legalNameToken(legalNameToken)
                .displayName(request.getDisplayName())
                .preferredLanguage(request.getPreferredLanguage().toLowerCase())
                .preferredChannel(request.getPreferredChannel())
                .quietStart(request.getQuietHours() != null ? request.getQuietHours().getStart() : null)
                .quietEnd(request.getQuietHours() != null ? request.getQuietHours().getEnd() : null)
                .quietTimezone(request.getQuietHours() != null ? request.getQuietHours().getTimezone() : null)
                .country(request.getRegion().getCountry().toUpperCase())
                .district(request.getRegion().getDistrict().toUpperCase())
                .ward(request.getRegion().getWard().toUpperCase())
                .village(request.getRegion().getVillage())
                .farmSizeBand(request.getFarmSizeBand())
                .primaryCropsCsv(String.join(",", request.getPrimaryCrops()))
                .attributesJson(writeJson(attrs))
                .enrolledByAgentId(agentId)
                .fraudHold(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        entity = profileRepository.save(entity);

        String acceptedGroupId = null;
        if (request.getGroupInvitationId() != null && !request.getGroupInvitationId().isBlank()) {
            UUID invId = parseUuid(request.getGroupInvitationId(), "VALIDATION_ERROR");
            Optional<GroupMembershipEntity> invOpt = membershipRepository.findById(invId);
            if (invOpt.isPresent() && invOpt.get().getStatus() == MembershipStatus.INVITED) {
                GroupMembershipEntity inv = invOpt.get();
                inv.setFarmerId(entity.getFarmerId());
                inv.setStatus(MembershipStatus.CONFIRMED);
                inv.setConfirmedAt(now);
                membershipRepository.save(inv);
                acceptedGroupId = inv.getGroupId().toString();
            }
        }

        WalletResponse walletResp = null;
        if (request.getWallet() != null) {
            walletResp = walletService.registerWalletInternal(entity, request.getWallet(), true);
        }

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "farmer.created");
        evt.put("farmerId", entity.getFarmerId().toString());
        evt.put("userId", entity.getUserId().toString());
        evt.put("preferredLanguage", entity.getPreferredLanguage());
        evt.put("preferredChannel", entity.getPreferredChannel().name());
        evt.put("region", regionMap(entity));
        if (acceptedGroupId != null) evt.put("groupId", acceptedGroupId);
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getFarmerTopic(), "farmer.created",
                entity.getFarmerId().toString(), evt);
        hookSdkClient.postCreate(entity.getFarmerId().toString());

        FarmerProfileResponse resp = FarmerProfileResponse.builder()
                .farmerId(entity.getFarmerId().toString())
                .userId(entity.getUserId().toString())
                .status(entity.getStatus().name())
                .preferences(preferencesMap(entity))
                .region(regionMap(entity))
                .farmSizeBand(entity.getFarmSizeBand().name())
                .primaryCrops(entity.getPrimaryCrops())
                .groupId(acceptedGroupId)
                .wallet(walletResp)
                .attributes(attrs)
                .version(entity.getVersion())
                .createdAt(now.toString())
                .build();
        idempotencyService.save(idempotencyKey, "FAR-01:" + userId, request, 201, resp);
        return resp;
    }

    public FarmerProfileViewResponse getProfile(
            UUID farmerId,
            String include,
            int payoutLimit,
            String delegationHeader) {
        FarmerIdentity actor = FarmerSecurityContext.require();
        FarmerProfileEntity profile = profileRepository.findById(farmerId)
                .orElseThrow(() -> new FarmerApiException(HttpStatus.NOT_FOUND, "FARMER_NOT_FOUND", "Farmer not found"));

        boolean isOwner = actor.userId() != null && actor.userId().equals(profile.getUserId());
        boolean isStaff = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.UNDERWRITER,
                AuthRole.COMPLIANCE_OFFICER, AuthRole.FINANCE, AuthRole.ADMIN);
        boolean isAgent = actor.hasRole(AuthRole.AGENT) && delegationHeader != null && !delegationHeader.isBlank();

        if (!isOwner && !isStaff) {
            if (isAgent && actor.userId() != null) {
                UUID delId = parseUuid(delegationHeader, "FARMER_NOT_FOUND");
                UUID delFarmerUserId = authKycClient.validateDelegation(delId, actor.userId(), DelegationScope.PROFILE_EDIT);
                if (!delFarmerUserId.equals(profile.getUserId())) {
                    throw new FarmerApiException(HttpStatus.NOT_FOUND, "FARMER_NOT_FOUND", "Farmer not found");
                }
            } else {
                throw new FarmerApiException(HttpStatus.NOT_FOUND, "FARMER_NOT_FOUND", "Farmer not found");
            }
        }

        Set<String> sections = parseInclude(include);
        boolean canSeeFinancial = isOwner || actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.FINANCE,
                AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN);
        boolean canSeePlotsAndPolicies = isOwner || actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.UNDERWRITER,
                AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN);

        FarmerProfileViewResponse.FarmerProfileViewResponseBuilder builder = FarmerProfileViewResponse.builder()
                .farmerId(profile.getFarmerId().toString())
                .userId(profile.getUserId().toString())
                .status(profile.getStatus().name())
                .preferences(preferencesMap(profile))
                .region(regionMap(profile))
                .farmSizeBand(profile.getFarmSizeBand().name())
                .primaryCrops(profile.getPrimaryCrops())
                .kycTier(eligibilityRepository.findById(profile.getUserId())
                        .map(e -> e.getKycTier().name()).orElse("TIER_1"))
                .attributes(hookSdkClient.enrichProfile(profile.getFarmerId().toString()))
                .version(profile.getVersion());

        if (sections.contains("wallets") && canSeeFinancial) {
            List<WalletResponse> wallets = walletRepository.findByFarmerIdOrderByCreatedAtAsc(farmerId).stream()
                    .map(w -> WalletResponse.builder()
                            .walletId(w.getWalletId().toString())
                            .provider(w.getProvider())
                            .maskedNumber(w.getMaskedNumber())
                            .status(w.getStatus().name())
                            .isPrimary(w.isPrimary())
                            .effectiveFrom(w.getEffectiveFrom().toString())
                            .reasonCode(w.getReasonCode())
                            .build())
                    .toList();
            builder.wallets(wallets);
        }
        if (sections.contains("group")) {
            membershipRepository.findFirstByFarmerIdAndStatusOrderByConfirmedAtDesc(farmerId, MembershipStatus.CONFIRMED)
                    .flatMap(m -> groupRepository.findById(m.getGroupId()))
                    .ifPresent(g -> builder.group(Map.of(
                            "groupId", g.getGroupId().toString(),
                            "name", g.getName(),
                            "status", MembershipStatus.CONFIRMED.name())));
        }
        if (sections.contains("plots") && canSeePlotsAndPolicies) {
            builder.plots(remoteSectionClient.fetchPlots(farmerId));
        }
        if (sections.contains("policies") && canSeePlotsAndPolicies) {
            builder.policies(remoteSectionClient.fetchPolicies(farmerId));
        }
        if (sections.contains("payouts") && canSeeFinancial) {
            builder.payouts(remoteSectionClient.fetchPayouts(farmerId, payoutLimit));
        }
        if (sections.contains("history") && actor.hasAnyRole(AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN)) {
            List<Map<String, Object>> hist = historyRepository.findByFarmerIdOrderByVersionAsc(farmerId).stream()
                    .map(h -> Map.<String, Object>of(
                            "version", h.getVersion(),
                            "changedFields", h.getChangedFieldsCsv(),
                            "actor", h.getActor(),
                            "timestamp", h.getCreatedAt().toString()))
                    .toList();
            builder.history(hist);
        }
        return builder.build();
    }

    @Transactional
    public FarmerProfileResponse updateProfile(
            UUID farmerId,
            String ifMatch,
            String delegationHeader,
            UpdateFarmerProfileRequest request) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new FarmerApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED",
                    "If-Match header is required");
        }
        if (request.getLegalName() != null || request.getPhone() != null || request.getPhoneNumber() != null
                || request.getWallet() != null || request.getStatus() != null
                || request.getKycTier() != null || request.getUserId() != null) {
            throw new FarmerApiException(HttpStatus.BAD_REQUEST, "FIELD_NOT_EDITABLE",
                    "This field cannot be changed here", Map.of("use", "FAR-04"));
        }

        FarmerIdentity actor = FarmerSecurityContext.require();
        FarmerProfileEntity profile = profileRepository.findById(farmerId)
                .orElseThrow(() -> new FarmerApiException(HttpStatus.NOT_FOUND, "FARMER_NOT_FOUND", "Farmer not found"));

        boolean isOwner = actor.userId() != null && actor.userId().equals(profile.getUserId());
        boolean isSupport = actor.hasRole(AuthRole.SUPPORT);
        if (!isOwner && !isSupport) {
            if (delegationHeader != null && !delegationHeader.isBlank() && actor.userId() != null) {
                UUID delId = parseUuid(delegationHeader, "FORBIDDEN");
                UUID targetUser = authKycClient.validateDelegation(delId, actor.userId(), DelegationScope.PROFILE_EDIT);
                if (!targetUser.equals(profile.getUserId())) {
                    throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
                }
            } else {
                throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
            }
        }

        int expectedVersion = parseIfMatch(ifMatch);
        if (!Objects.equals(profile.getVersion(), expectedVersion)) {
            throw new FarmerApiException(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT",
                    "Profile changed. Reload and retry", Map.of("currentVersion", profile.getVersion()));
        }

        List<String> changedFields = new ArrayList<>();
        if (request.getPreferredLanguage() != null && !request.getPreferredLanguage().equalsIgnoreCase(profile.getPreferredLanguage())) {
            validateLanguageAndCrops(request.getPreferredLanguage(), List.of("MAIZE"), profile.getCountry());
            profile.setPreferredLanguage(request.getPreferredLanguage().toLowerCase());
            changedFields.add("preferredLanguage");
        }
        if (request.getPreferredChannel() != null && request.getPreferredChannel() != profile.getPreferredChannel()) {
            profile.setPreferredChannel(request.getPreferredChannel());
            changedFields.add("preferredChannel");
        }
        if (request.getQuietHours() != null) {
            profile.setQuietStart(request.getQuietHours().getStart());
            profile.setQuietEnd(request.getQuietHours().getEnd());
            profile.setQuietTimezone(request.getQuietHours().getTimezone());
            changedFields.add("quietHours");
        }
        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName());
            changedFields.add("displayName");
        }
        if (request.getRegion() != null) {
            profile.setCountry(request.getRegion().getCountry().toUpperCase());
            profile.setDistrict(request.getRegion().getDistrict().toUpperCase());
            profile.setWard(request.getRegion().getWard().toUpperCase());
            profile.setVillage(request.getRegion().getVillage());
            changedFields.add("region");
        }
        if (request.getFarmSizeBand() != null) {
            profile.setFarmSizeBand(request.getFarmSizeBand());
            changedFields.add("farmSizeBand");
        }
        if (request.getPrimaryCrops() != null && !request.getPrimaryCrops().isEmpty()) {
            validateLanguageAndCrops(profile.getPreferredLanguage(), request.getPrimaryCrops(), profile.getCountry());
            profile.setPrimaryCrops(request.getPrimaryCrops());
            changedFields.add("primaryCrops");
        }

        Instant now = Instant.now();
        profile.setUpdatedAt(now);
        profile = profileRepository.saveAndFlush(profile);

        if (request.getGroupInvitationResponse() != null && request.getGroupInvitationResponse().getInvitationId() != null) {
            UUID invId = parseUuid(request.getGroupInvitationResponse().getInvitationId(), "VALIDATION_ERROR");
            membershipRepository.findById(invId).ifPresent(m -> {
                if (m.getStatus() == MembershipStatus.INVITED) {
                    boolean confirm = "CONFIRM".equalsIgnoreCase(request.getGroupInvitationResponse().getResponse());
                    m.setFarmerId(farmerId);
                    m.setStatus(confirm ? MembershipStatus.CONFIRMED : MembershipStatus.DECLINED);
                    m.setConfirmedAt(now);
                    membershipRepository.save(m);

                    Map<String, Object> gEvt = new LinkedHashMap<>();
                    gEvt.put("eventType", "farmer.group.member.confirmed");
                    gEvt.put("groupId", m.getGroupId().toString());
                    gEvt.put("farmerId", farmerId.toString());
                    gEvt.put("invitationId", invId.toString());
                    gEvt.put("response", m.getStatus().name());
                    gEvt.put("timestamp", now.toString());
                    outboxService.enqueue(properties.getKafka().getFarmerTopic(),
                            "farmer.group.member.confirmed", m.getGroupId().toString(), gEvt);
                }
            });
        }

        if (!changedFields.isEmpty()) {
            historyRepository.save(ProfileHistoryEntity.builder()
                    .historyId(UUID.randomUUID())
                    .farmerId(farmerId)
                    .version(profile.getVersion())
                    .changedFieldsCsv(String.join(",", changedFields))
                    .diffJson(writeJson(Map.of("changedFields", changedFields)))
                    .actor(actor.actorId())
                    .delegationId(delegationHeader)
                    .createdAt(now)
                    .build());

            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", "farmer.updated");
            evt.put("farmerId", farmerId.toString());
            evt.put("changedFields", changedFields);
            evt.put("version", profile.getVersion());
            evt.put("updatedBy", actor.actorId());
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getFarmerTopic(), "farmer.updated",
                    farmerId.toString(), evt);
            hookSdkClient.postUpdate(farmerId.toString());
        }

        return FarmerProfileResponse.builder()
                .farmerId(profile.getFarmerId().toString())
                .changedFields(changedFields)
                .preferences(preferencesMap(profile))
                .region(regionMap(profile))
                .farmSizeBand(profile.getFarmSizeBand().name())
                .primaryCrops(profile.getPrimaryCrops())
                .version(profile.getVersion())
                .updatedAt(now.toString())
                .build();
    }

    public FarmerSearchResponse searchFarmers(
            String phoneNumber,
            String name,
            String region,
            String groupId,
            FarmerStatus status,
            String reason,
            int page,
            int size) {
        FarmerIdentity actor = FarmerSecurityContext.require();
        boolean allowed = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.UNDERWRITER,
                AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN, AuthRole.AGENT);
        if (!allowed) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (size > 50 || size < 1 || page < 0) {
            throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "size must be between 1 and 50");
        }
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            if (reason == null || reason.trim().length() < 5) {
                throw new FarmerApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED",
                        "A reason is required for phone lookups");
            }
            rateLimitService.check("search:phone:" + actor.actorId(), 30, 3600);
            dataVaultClient.hash(phoneNumber.trim(), "PHONE_LOOKUP");
        } else {
            if ((name == null || name.isBlank()) && (region == null || region.isBlank())
                    && (groupId == null || groupId.isBlank()) && status == null) {
                throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "At least one search filter is required");
            }
            if (name != null && !name.isBlank() && (region == null || region.isBlank())
                    && (groupId == null || groupId.isBlank())) {
                throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "name search requires region or groupId");
            }
            rateLimitService.check("search:all:" + actor.actorId(), 120, 3600);
        }

        List<FarmerProfileEntity> all = profileRepository.findByStatusNot(FarmerStatus.CLOSED);
        boolean isAgentOnly = actor.hasRole(AuthRole.AGENT) && !actor.hasAnyRole(
                AuthRole.SUPPORT, AuthRole.UNDERWRITER, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN);

        List<FarmerSearchResponse.FarmerSummary> matched = all.stream()
                .filter(p -> !isAgentOnly || (actor.userId() != null && actor.userId().equals(p.getEnrolledByAgentId())))
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> region == null || region.isBlank()
                        || p.getDistrict().equalsIgnoreCase(region) || p.getWard().equalsIgnoreCase(region))
                .filter(p -> name == null || name.isBlank()
                        || (p.getDisplayName() != null && p.getDisplayName().toLowerCase().startsWith(name.toLowerCase())))
                .map(p -> FarmerSearchResponse.FarmerSummary.builder()
                        .farmerId(p.getFarmerId().toString())
                        .displayName(p.getDisplayName())
                        .region(p.getDistrict() + " / " + p.getWard())
                        .status(p.getStatus().name())
                        .build())
                .toList();

        int from = Math.min(page * size, matched.size());
        int to = Math.min(from + size, matched.size());
        int totalPages = (int) Math.ceil((double) matched.size() / size);
        return FarmerSearchResponse.builder()
                .items(matched.subList(from, to))
                .page(page)
                .size(size)
                .totalElements(matched.size())
                .totalPages(totalPages)
                .build();
    }

    public PayoutDestinationSnapshot getPayoutDestination(UUID farmerId, Instant asOf, boolean checkOnly) {
        FarmerProfileEntity profile = profileRepository.findById(farmerId)
                .orElseThrow(() -> new FarmerApiException(HttpStatus.NOT_FOUND, "FARMER_NOT_FOUND", "Farmer not found"));
        if (profile.getStatus() == FarmerStatus.RESTRICTED || profile.getStatus() == FarmerStatus.CLOSED) {
            return new PayoutDestinationSnapshot(false, null, null, null, null, null,
                    "PROFILE_RESTRICTED", false, asOf.toString());
        }
        if (profile.isFraudHold()) {
            return new PayoutDestinationSnapshot(false, null, null, null, null, null,
                    "FRAUD_HOLD", false, asOf.toString());
        }

        List<FarmerWalletEntity> wallets = walletRepository.findByFarmerIdOrderByCreatedAtAsc(farmerId);
        List<FarmerWalletEntity> verifiedWallets = wallets.stream()
                .filter(w -> w.getStatus() == WalletStatus.VERIFIED)
                .toList();
        boolean anyUnderReview = wallets.stream()
                .anyMatch(w -> w.getStatus() == WalletStatus.PENDING_REVIEW || w.getStatus() == WalletStatus.PENDING_SECOND_REVIEW);
        boolean pendingChange = verifiedWallets.stream()
                .anyMatch(w -> w.isPrimary() && w.getEffectiveFrom().isAfter(asOf));

        Optional<FarmerWalletEntity> chosenPrimary = verifiedWallets.stream()
                .filter(w -> w.isPrimary() && !w.getEffectiveFrom().isAfter(asOf))
                .max(Comparator.comparing(FarmerWalletEntity::getEffectiveFrom));

        // If new primary is cooling off, pick earlier verified wallet
        if (chosenPrimary.isEmpty() && pendingChange) {
            chosenPrimary = verifiedWallets.stream()
                    .filter(w -> !w.getEffectiveFrom().isAfter(asOf))
                    .findFirst();
        }
        if (chosenPrimary.isEmpty()) {
            String hold = anyUnderReview ? "WALLET_UNDER_REVIEW" : "NO_VERIFIED_WALLET";
            return new PayoutDestinationSnapshot(false, null, null, null, null, null,
                    hold, pendingChange, asOf.toString());
        }

        FarmerWalletEntity chosen = chosenPrimary.get();
        String fallbackId = verifiedWallets.stream()
                .filter(w -> !w.getWalletId().equals(chosen.getWalletId()))
                .map(w -> w.getWalletId().toString())
                .findFirst().orElse(null);

        return new PayoutDestinationSnapshot(
                true,
                chosen.getWalletId().toString(),
                chosen.getProvider(),
                chosen.getMaskedNumber(),
                checkOnly ? null : chosen.getWalletNumberToken(),
                fallbackId,
                "NONE",
                pendingChange,
                asOf.toString()
        );
    }

    public FarmerContextSnapshot getFarmerContext(UUID farmerId) {
        FarmerProfileEntity profile = profileRepository.findById(farmerId)
                .orElseThrow(() -> new FarmerApiException(HttpStatus.NOT_FOUND, "FARMER_NOT_FOUND", "Farmer not found"));
        CooperativeGroupEntity group = membershipRepository
                .findFirstByFarmerIdAndStatusOrderByConfirmedAtDesc(farmerId, MembershipStatus.CONFIRMED)
                .flatMap(m -> groupRepository.findById(m.getGroupId()))
                .orElse(null);
        return new FarmerContextSnapshot(profile, group, Instant.now().toString());
    }

    @Transactional
    public void onKycVerified(UUID userId, KycTier tier, String legalNameToken) {
        eligibilityRepository.save(EligibilityRecordEntity.builder()
                .userId(userId)
                .kycTier(tier)
                .legalNameToken(legalNameToken != null ? legalNameToken : "vt_name_default")
                .verifiedAt(Instant.now())
                .build());
    }

    @Transactional
    public void onDataRightsReceived(UUID requestId, UUID userId, String type) {
        if ("ERASURE".equalsIgnoreCase(type)) {
            profileRepository.findByUserId(userId).ifPresent(p -> {
                p.setStatus(FarmerStatus.CLOSED);
                p.setDisplayName(null);
                p.setUpdatedAt(Instant.now());
                profileRepository.save(p);
                dataVaultClient.shredSubject(userId);
            });
        }
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "data.request.part.completed");
        evt.put("requestId", requestId.toString());
        evt.put("serviceName", "farmer-profile-service");
        evt.put("retainedCategories", "ERASURE".equalsIgnoreCase(type) ? List.of("PAYMENT") : List.of());
        outboxService.enqueue(properties.getKafka().getIdentityTopic(), "data.request.part.completed",
                requestId.toString(), evt);
    }

    @Transactional
    public void onFraudFlagged(UUID userId) {
        profileRepository.findByUserId(userId).ifPresent(p -> {
            p.setFraudHold(true);
            profileRepository.save(p);
        });
    }

    private void validateLanguageAndCrops(String lang, List<String> crops, String country) {
        if (!properties.getCatalogue().getSupportedLanguages().contains(lang.toLowerCase())) {
            throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported language");
        }
        if (!properties.getCatalogue().getSupportedCountries().contains(country.toUpperCase())) {
            throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported country");
        }
        for (String c : crops) {
            if (!properties.getCatalogue().getSupportedCrops().contains(c.toUpperCase())) {
                throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unsupported crop: " + c);
            }
        }
    }

    private int parseIfMatch(String header) {
        try {
            return Integer.parseInt(header.replace("\"", "").trim());
        } catch (NumberFormatException e) {
            throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid If-Match ETag");
        }
    }

    private UUID parseUuid(String raw, String code) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            HttpStatus st = "FORBIDDEN".equals(code) ? HttpStatus.FORBIDDEN
                    : ("FARMER_NOT_FOUND".equals(code) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST);
            throw new FarmerApiException(st, code, "Invalid identifier");
        }
    }

    private Set<String> parseInclude(String include) {
        if (include == null || include.isBlank()) return Set.of();
        if ("all".equalsIgnoreCase(include.trim())) {
            return Set.of("wallets", "plots", "policies", "payouts", "group", "history");
        }
        Set<String> valid = Set.of("wallets", "plots", "policies", "payouts", "group", "history");
        Set<String> res = new HashSet<>();
        for (String s : include.split(",")) {
            String t = s.trim().toLowerCase();
            if (!t.isEmpty()) {
                if (!valid.contains(t)) {
                    throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unknown include: " + t);
                }
                res.add(t);
            }
        }
        return res;
    }

    private Map<String, Object> preferencesMap(FarmerProfileEntity e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("preferredLanguage", e.getPreferredLanguage());
        map.put("preferredChannel", e.getPreferredChannel().name());
        if (e.getDisplayName() != null) map.put("displayName", e.getDisplayName());
        if (e.getQuietStart() != null) {
            map.put("quietHours", Map.of(
                    "start", e.getQuietStart(),
                    "end", e.getQuietEnd(),
                    "timezone", e.getQuietTimezone() != null ? e.getQuietTimezone() : "UTC"));
        }
        return map;
    }

    private Map<String, Object> regionMap(FarmerProfileEntity e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("country", e.getCountry());
        map.put("district", e.getDistrict());
        map.put("ward", e.getWard());
        if (e.getVillage() != null) map.put("village", e.getVillage());
        return map;
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    public record PayoutDestinationSnapshot(
            boolean usable, String walletId, String provider, String maskedNumber, String walletToken,
            String fallbackWalletId, String hold, boolean pendingChange, String asOf) {}

    public record FarmerContextSnapshot(FarmerProfileEntity profile, CooperativeGroupEntity group, String asOf) {}
}
