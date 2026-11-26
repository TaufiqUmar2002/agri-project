package com.umar.repository.keyManagement;

import com.umar.entity.keyManagement.Secret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretRepository extends JpaRepository<Secret, UUID> {

    Optional<Secret> findByRef(String ref);
}
