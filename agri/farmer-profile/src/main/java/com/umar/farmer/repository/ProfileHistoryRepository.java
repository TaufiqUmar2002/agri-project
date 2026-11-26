package com.umar.farmer.repository;

import com.umar.farmer.domain.entity.ProfileHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileHistoryRepository extends JpaRepository<ProfileHistoryEntity, UUID> {
    List<ProfileHistoryEntity> findByFarmerIdOrderByVersionAsc(UUID farmerId);
}
