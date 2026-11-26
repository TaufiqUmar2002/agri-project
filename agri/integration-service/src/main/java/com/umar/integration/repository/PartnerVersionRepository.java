package com.umar.integration.repository;

import com.umar.integration.domain.entity.PartnerVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerVersionRepository extends JpaRepository<PartnerVersionEntity, String> {
    List<PartnerVersionEntity> findByPartnerIdOrderByVersionDesc(String partnerId);
}
