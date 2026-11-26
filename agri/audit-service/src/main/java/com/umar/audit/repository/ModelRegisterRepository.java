package com.umar.audit.repository;

import com.umar.audit.domain.ModelRegisterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModelRegisterRepository extends JpaRepository<ModelRegisterEntity, UUID> {
    List<ModelRegisterEntity> findByModelIdOrderByEffectiveFromDesc(String modelId);
}
