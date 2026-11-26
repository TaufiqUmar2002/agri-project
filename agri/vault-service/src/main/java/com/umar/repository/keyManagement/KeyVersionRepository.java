package com.umar.repository.keyManagement;

import com.umar.entity.keyManagement.KeyVersion;
import com.umar.enums.vault.LEVEL;
import com.umar.enums.vault.State;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KeyVersionRepository extends JpaRepository<KeyVersion, UUID> {

    Optional<KeyVersion> findFirstByLevelAndScopeKeyAndStateOrderByCreatedAtDesc(LEVEL level, String scopeKey, State state);

    Optional<KeyVersion> findFirstByLevelAndScopeKeyOrderByCreatedAtDesc(LEVEL level, String scopeKey);

    List<KeyVersion> findByLevelAndScopeKey(LEVEL level, String scopeKey);

    List<KeyVersion> findByLevelAndState(LEVEL level, State state);
}
