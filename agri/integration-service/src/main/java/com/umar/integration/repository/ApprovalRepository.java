package com.umar.integration.repository;

import com.umar.integration.domain.entity.ApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {
    List<ApprovalEntity> findBySubjectIdOrderByCreatedAtDesc(String subjectId);
}
