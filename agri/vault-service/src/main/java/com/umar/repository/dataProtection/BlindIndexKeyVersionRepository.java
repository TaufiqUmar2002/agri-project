package com.umar.repository.dataProtection;

import com.umar.entity.dataProtection.BlindIndexKeyVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlindIndexKeyVersionRepository extends JpaRepository<BlindIndexKeyVersion, UUID> {

    List<BlindIndexKeyVersion> findByDataClassAndStateIn(String dataClass, Collection<String> states);

    Optional<BlindIndexKeyVersion> findFirstByDataClassAndStateOrderByVersionDesc(String dataClass, String state);

    Optional<BlindIndexKeyVersion> findById(UUID id);
}