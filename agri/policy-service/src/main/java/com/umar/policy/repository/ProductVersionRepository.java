package com.umar.policy.repository;

import com.umar.enums.policy.ProductVersionStatus;
import com.umar.policy.entity.ProductVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVersionRepository extends JpaRepository<ProductVersionEntity, String> {
    Optional<ProductVersionEntity> findByProductIdAndVersionNum(String productId, Integer versionNum);
    Optional<ProductVersionEntity> findFirstByProductCodeOrderByVersionNumDesc(String productCode);
    Optional<ProductVersionEntity> findFirstByProductIdOrderByVersionNumDesc(String productId);
    Optional<ProductVersionEntity> findByProductCodeAndDefinitionHash(String productCode, String definitionHash);
    List<ProductVersionEntity> findByProductIdAndStatusIn(String productId, Collection<ProductVersionStatus> statuses);
    List<ProductVersionEntity> findByStatus(ProductVersionStatus status);
    List<ProductVersionEntity> findByProductCode(String productCode);
}
