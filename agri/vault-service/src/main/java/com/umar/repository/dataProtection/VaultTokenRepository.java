package com.umar.repository.dataProtection;

import com.umar.entity.dataProtection.VaultToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface VaultTokenRepository extends JpaRepository<VaultToken, UUID> {

    @Query("SELECT t FROM VaultToken t WHERE t.subjectRef = :subjectRef AND t.dataClass = :dataClass")
    Optional<VaultToken> findExistingMatch( String subjectRef, String dataClass);
}
