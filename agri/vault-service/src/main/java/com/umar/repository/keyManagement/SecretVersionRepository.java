package com.umar.repository.keyManagement;

import com.umar.entity.keyManagement.SecretVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretVersionRepository extends JpaRepository<SecretVersion, UUID> {

    List<SecretVersion> findBySecret_IdOrderByVersionDesc(UUID secretId);

    Optional<SecretVersion> findFirstBySecret_IdAndStateOrderByVersionDesc(UUID secretId, String state);
}
