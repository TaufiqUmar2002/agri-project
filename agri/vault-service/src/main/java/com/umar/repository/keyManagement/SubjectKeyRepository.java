package com.umar.repository.keyManagement;

import com.umar.entity.keyManagement.SubjectKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubjectKeyRepository extends JpaRepository<SubjectKey, UUID> {

    Optional<SubjectKey> findBySubjectRefAndRetentionGroup(String subjectRef, String retentionGroup);

    List<SubjectKey> findBySubjectRef(String subjectRef);

    List<SubjectKey> findByState(String state);

    List<SubjectKey> findByClassKekVersionAndState(String classKekVersion, String state);
}
