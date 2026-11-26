package com.umar.auth.repository;

import com.umar.auth.domain.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {
    Optional<UserAccountEntity> findByPhoneHash(String phoneHash);
    Optional<UserAccountEntity> findByUsername(String username);
}
