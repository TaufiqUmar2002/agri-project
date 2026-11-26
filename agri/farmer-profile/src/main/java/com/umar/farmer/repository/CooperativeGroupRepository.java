package com.umar.farmer.repository;

import com.umar.farmer.domain.entity.CooperativeGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CooperativeGroupRepository extends JpaRepository<CooperativeGroupEntity, UUID> {
    Optional<CooperativeGroupEntity> findByCountryAndRegistrationNumber(String country, String registrationNumber);
    Optional<CooperativeGroupEntity> findByCountryAndDistrictAndNameIgnoreCase(String country, String district, String name);
}
