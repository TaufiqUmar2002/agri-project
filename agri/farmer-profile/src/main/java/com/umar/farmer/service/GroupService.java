package com.umar.farmer.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.farmer.GroupStatus;
import com.umar.enums.farmer.GroupType;
import com.umar.enums.farmer.MembershipStatus;
import com.umar.farmer.client.AuthKycClient;
import com.umar.farmer.client.DataVaultClient;
import com.umar.farmer.client.HookSdkClient;
import com.umar.farmer.config.FarmerProperties;
import com.umar.farmer.domain.entity.CooperativeGroupEntity;
import com.umar.farmer.domain.entity.GroupMembershipEntity;
import com.umar.farmer.exception.FarmerApiException;
import com.umar.farmer.repository.CooperativeGroupRepository;
import com.umar.farmer.repository.GroupMembershipRepository;
import com.umar.farmer.security.FarmerIdentity;
import com.umar.farmer.security.FarmerSecurityContext;
import com.umar.farmer.service.support.FarmerIdempotencyService;
import com.umar.farmer.service.support.FarmerOutboxService;
import com.umar.farmer.service.support.FarmerRateLimitService;
import com.umar.payload.request.farmer.CreateGroupRequest;
import com.umar.payload.request.farmer.InviteGroupMembersRequest;
import com.umar.payload.response.farmer.GroupResponse;
import com.umar.payload.response.farmer.InviteGroupMembersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final CooperativeGroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final AuthKycClient authKycClient;
    private final DataVaultClient dataVaultClient;
    private final HookSdkClient hookSdkClient;
    private final FarmerOutboxService outboxService;
    private final FarmerIdempotencyService idempotencyService;
    private final FarmerRateLimitService rateLimitService;
    private final FarmerProperties properties;

    @Transactional
    public GroupResponse createGroup(String idempotencyKey, CreateGroupRequest request) {
        FarmerIdentity actor = FarmerSecurityContext.require();
        boolean isAdmin = actor.hasRole(AuthRole.ADMIN);
        boolean isAgent = actor.hasRole(AuthRole.AGENT);
        if (!isAdmin && !isAgent) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (request.getGroupType() == GroupType.SPONSOR_COHORT && !isAdmin) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "SPONSOR_COHORT requires Administrator");
        }

        Optional<GroupResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "FAR-05", request, GroupResponse.class);
        if (replay.isPresent()) return replay.get();

        UUID leaderId = request.getLeaderUserId() != null ? UUID.fromString(request.getLeaderUserId()) : actor.userId();
        if (leaderId == null) {
            throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "leaderUserId is required");
        }
        AuthKycClient.IdentitySnapshot leaderSnap = authKycClient.getIdentityContext(leaderId);
        if (!"ACTIVE".equals(leaderSnap.accountStatus()) || !leaderSnap.roles().contains("AGENT")) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "AGENT_NOT_ACTIVE",
                    "Your agent account is not active");
        }

        if (request.getGroupType() == GroupType.COOPERATIVE) {
            if (request.getRegistrationNumber() == null || request.getRegistrationNumber().isBlank()) {
                throw new FarmerApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "registrationNumber is required for COOPERATIVE");
            }
            groupRepository.findByCountryAndRegistrationNumber(
                            request.getRegion().getCountry().toUpperCase(), request.getRegistrationNumber().trim())
                    .ifPresent(existing -> {
                        throw new FarmerApiException(HttpStatus.CONFLICT, "GROUP_EXISTS",
                                "This group already exists", Map.of("groupId", existing.getGroupId().toString()));
                    });
        }
        groupRepository.findByCountryAndDistrictAndNameIgnoreCase(
                        request.getRegion().getCountry().toUpperCase(),
                        request.getRegion().getDistrict().toUpperCase(),
                        request.getName().trim())
                .ifPresent(existing -> {
                    throw new FarmerApiException(HttpStatus.CONFLICT, "GROUP_EXISTS",
                            "This group already exists", Map.of("groupId", existing.getGroupId().toString()));
                });

        HookSdkClient.GroupHookDecision hookDecision = hookSdkClient.preCreateGroup(
                request.getName(), request.getRegistrationNumber());
        GroupStatus status = (request.getGroupType() == GroupType.SPONSOR_COHORT && isAdmin
                && hookDecision == HookSdkClient.GroupHookDecision.ALLOW)
                ? GroupStatus.ACTIVE : GroupStatus.PENDING_VERIFICATION;

        Instant now = Instant.now();
        int maxMembers = request.getMaxMembers() != null ? request.getMaxMembers() : 500;
        CooperativeGroupEntity saved = groupRepository.save(CooperativeGroupEntity.builder()
                .groupId(UUID.randomUUID())
                .name(request.getName().trim())
                .groupType(request.getGroupType())
                .registrationNumber(request.getRegistrationNumber())
                .country(request.getRegion().getCountry().toUpperCase())
                .district(request.getRegion().getDistrict().toUpperCase())
                .leaderUserId(leaderId)
                .sponsorOrgId(request.getSponsorOrgId())
                .status(status)
                .maxMembers(maxMembers)
                .createdAt(now)
                .build());

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "farmer.group.created");
        evt.put("groupId", saved.getGroupId().toString());
        evt.put("groupType", saved.getGroupType().name());
        evt.put("leaderUserId", saved.getLeaderUserId().toString());
        evt.put("status", saved.getStatus().name());
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getFarmerTopic(), "farmer.group.created",
                saved.getGroupId().toString(), evt);

        GroupResponse resp = GroupResponse.builder()
                .groupId(saved.getGroupId().toString())
                .name(saved.getName())
                .groupType(saved.getGroupType().name())
                .status(saved.getStatus().name())
                .leaderUserId(saved.getLeaderUserId().toString())
                .maxMembers(saved.getMaxMembers())
                .createdAt(now.toString())
                .build();
        idempotencyService.save(idempotencyKey, "FAR-05", request, 201, resp);
        return resp;
    }

    @Transactional
    public InviteGroupMembersResponse inviteMembers(
            UUID groupId,
            String idempotencyKey,
            InviteGroupMembersRequest request) {
        FarmerIdentity actor = FarmerSecurityContext.require();
        CooperativeGroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new FarmerApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Group not found"));

        boolean isAdmin = actor.hasRole(AuthRole.ADMIN);
        boolean isLeader = actor.userId() != null && actor.userId().equals(group.getLeaderUserId());
        if (!isAdmin && !isLeader) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised for this group");
        }
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new FarmerApiException(HttpStatus.CONFLICT, "GROUP_NOT_ACTIVE",
                    "This group cannot invite members yet");
        }

        rateLimitService.check("invite:" + actor.actorId(), 1000, 86400);

        Optional<InviteGroupMembersResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "FAR-06:" + groupId, request, InviteGroupMembersResponse.class);
        if (replay.isPresent()) return replay.get();

        long currentCount = membershipRepository.countByGroupIdAndStatusIn(
                groupId, List.of(MembershipStatus.INVITED, MembershipStatus.CONFIRMED));
        if (currentCount + request.getMembers().size() > group.getMaxMembers()) {
            throw new FarmerApiException(HttpStatus.UNPROCESSABLE_ENTITY, "GROUP_CAPACITY_EXCEEDED",
                    "Group limit reached");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(14, ChronoUnit.DAYS);
        Set<String> seenInCall = new HashSet<>();
        List<InviteGroupMembersResponse.RowResult> results = new ArrayList<>();
        int accepted = 0, skipped = 0, rejected = 0;

        for (int i = 0; i < request.getMembers().size(); i++) {
            InviteGroupMembersRequest.InviteRow row = request.getMembers().get(i);
            String phone = row.getPhoneNumber() != null ? row.getPhoneNumber().trim() : "";
            if (!phone.matches("^\\+[1-9]\\d{7,14}$")) {
                rejected++;
                results.add(InviteGroupMembersResponse.RowResult.builder()
                        .index(i).status("REJECTED").reasonCode("INVALID_PHONE").build());
                continue;
            }
            if (!seenInCall.add(phone)) {
                skipped++;
                results.add(InviteGroupMembersResponse.RowResult.builder()
                        .index(i).status("SKIPPED").reasonCode("DUPLICATE_IN_REQUEST").build());
                continue;
            }
            String phoneHash = dataVaultClient.hash(phone, "PHONE_LOOKUP");
            Optional<GroupMembershipEntity> existing = membershipRepository.findByGroupIdAndPhoneHashAndStatusIn(
                    groupId, phoneHash, List.of(MembershipStatus.INVITED, MembershipStatus.CONFIRMED));
            if (existing.isPresent()) {
                skipped++;
                results.add(InviteGroupMembersResponse.RowResult.builder()
                        .index(i).status("SKIPPED").reasonCode("ALREADY_INVITED_OR_MEMBER").build());
                continue;
            }

            String phoneToken = dataVaultClient.tokenize("PHONE", phone, groupId.toString(), "GROUP_INVITATION");
            GroupMembershipEntity m = membershipRepository.save(GroupMembershipEntity.builder()
                    .membershipId(UUID.randomUUID())
                    .groupId(groupId)
                    .phoneHash(phoneHash)
                    .phoneToken(phoneToken)
                    .status(MembershipStatus.INVITED)
                    .invitedAt(now)
                    .expiresAt(expiresAt)
                    .build());
            accepted++;
            results.add(InviteGroupMembersResponse.RowResult.builder()
                    .index(i).status("INVITED").build());

            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", "farmer.group.member.invited");
            evt.put("groupId", groupId.toString());
            evt.put("invitationId", m.getMembershipId().toString());
            evt.put("phoneHash", phoneHash);
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getFarmerTopic(), "farmer.group.member.invited",
                    groupId.toString(), evt);
        }

        InviteGroupMembersResponse resp = InviteGroupMembersResponse.builder()
                .accepted(accepted)
                .skipped(skipped)
                .rejected(rejected)
                .results(results)
                .expiresAt(expiresAt.toString())
                .build();
        idempotencyService.save(idempotencyKey, "FAR-06:" + groupId, request, 207, resp);
        return resp;
    }
}
