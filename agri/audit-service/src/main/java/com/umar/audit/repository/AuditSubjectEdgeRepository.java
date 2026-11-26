package com.umar.audit.repository;

import com.umar.audit.domain.AuditSubjectEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditSubjectEdgeRepository extends JpaRepository<AuditSubjectEdgeEntity, UUID> {
    List<AuditSubjectEdgeEntity> findBySubjectTypeAndSubjectId(String subjectType, String subjectId);
}
