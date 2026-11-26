package com.umar.farmer.repository;

import com.umar.enums.farmer.MembershipStatus;
import com.umar.farmer.domain.entity.GroupMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMembershipRepository extends JpaRepository<GroupMembershipEntity, UUID> {
    List<GroupMembershipEntity> findByFarmerId(UUID farmerId);
    Optional<GroupMembershipEntity> findFirstByFarmerIdAndStatusOrderByConfirmedAtDesc(UUID farmerId, MembershipStatus status);
    Optional<GroupMembershipEntity> findByGroupIdAndPhoneHashAndStatusIn(
            UUID groupId, String phoneHash, Collection<MembershipStatus> statuses);
    long countByGroupIdAndStatusIn(UUID groupId, Collection<MembershipStatus> statuses);
}
