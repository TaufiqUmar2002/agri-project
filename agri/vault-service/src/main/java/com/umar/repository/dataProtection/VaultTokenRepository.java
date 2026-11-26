package com.umar.repository.dataProtection;

import com.umar.entity.dataProtection.VaultToken;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaultTokenRepository extends JpaRepository<VaultToken, UUID> {

    @Query("SELECT t FROM VaultToken t WHERE t.subjectRef = :subjectRef AND t.dataClass = :dataClass")
    Optional<VaultToken> findExistingMatch( String subjectRef, String dataClass);

    Optional<VaultToken> findByToken(String token);

    List<VaultToken> findBySubjectRef(String subjectRef);

    List<VaultToken> findBySubjectRefAndRetentionGroup(String subjectRef, String retentionGroup);

    List<VaultToken> findBySubjectKeyId(UUID subjectKeyId);

    List<VaultToken> findByDataClassOrderByIdAsc(String dataClass, Pageable pageable);

    List<VaultToken> findByDataClassAndIdGreaterThanOrderByIdAsc(String dataClass, UUID afterId, Pageable pageable);

    long countByDataClass(String dataClass);
}
