package com.umar.repository.keyManagement;

import com.umar.entity.keyManagement.ErasureRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ErasureRegistryRepository extends JpaRepository<ErasureRegistry, UUID> {

    List<ErasureRegistry> findBySubjectHash(String subjectHash);

    Optional<ErasureRegistry> findBySubjectHashAndGroup(String subjectHash, String group);

    Optional<ErasureRegistry> findFirstByRequestId(String requestId);

    boolean existsBySubjectHashAndGroupAndShreddedAtIsNotNull(String subjectHash, String group);
}
