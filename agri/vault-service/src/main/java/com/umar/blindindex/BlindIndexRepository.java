package com.umar.blindindex;


import com.umar.entity.dataProtection.BlindIndexEntry;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlindIndexRepository
        extends JpaRepository<BlindIndexEntry, UUID> {

    Optional<BlindIndexEntry>
    findByTokenIdAndIndexKeyVersionId(
            UUID tokenId,
            UUID indexKeyVersionId);

    List<BlindIndexEntry>
    findByDataClassAndSubjectRefAndIndexKeyVersionIdAndIndexHash(
            String dataClass,
            String subjectRef,
            UUID indexKeyVersionId,
            byte[] indexHash);

    List<BlindIndexEntry>
    findByDataClassAndIndexKeyVersionIdAndIndexHash(
            String dataClass,
            UUID indexKeyVersionId,
            byte[] indexHash);

    boolean existsByDataClassAndIndexKeyVersionId(
            String dataClass,
            UUID indexKeyVersionId);

    long countByDataClassAndIndexKeyVersionId(
            String dataClass,
            UUID indexKeyVersionId);

    @Query("""
        SELECT COUNT(b)
        FROM BlindIndexEntry b
        WHERE b.dataClass = :dataClass
          AND b.indexKeyVersionId = :keyVersion
        """)
    long countForVersion(
            @Param("dataClass") String dataClass,
            @Param("keyVersion") UUID keyVersion);

    @Modifying
    @Query("""
        DELETE FROM BlindIndexEntry b
        WHERE b.dataClass = :dataClass
          AND b.indexKeyVersionId = :keyVersion
        """)
    int deleteByDataClassAndKeyVersion(
            @Param("dataClass") String dataClass,
            @Param("keyVersion") UUID keyVersion);
}
