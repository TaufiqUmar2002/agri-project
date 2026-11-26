package com.umar.repository.dataProtection;

import com.umar.entity.dataProtection.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByToken(String token);

    List<Document> findByDataClass(String dataClass);

    List<Document> findBySubjectRef(String subjectRef);

    List<Document> findBySubjectRefAndRetentionGroup(String subjectRef, String retentionGroup);

    List<Document> findByDataClassAndSubjectRef(String dataClass, String subjectRef);
}
