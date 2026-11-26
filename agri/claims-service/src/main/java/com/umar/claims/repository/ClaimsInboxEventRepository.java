package com.umar.claims.repository;

import com.umar.claims.domain.ClaimsInboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimsInboxEventRepository extends JpaRepository<ClaimsInboxEventEntity, String> {
}
