package com.umar.policy.repository;

import com.umar.enums.auth.AuthRole;
import com.umar.policy.entity.ProductApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductApprovalRepository extends JpaRepository<ProductApprovalEntity, UUID> {
    List<ProductApprovalEntity> findByProductIdAndVersionNum(String productId, Integer versionNum);
    Optional<ProductApprovalEntity> findByProductIdAndVersionNumAndApproverRole(
            String productId, Integer versionNum, AuthRole approverRole);
}
