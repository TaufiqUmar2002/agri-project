package com.umar.riskpool.repository;

import com.umar.riskpool.domain.JournalTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JournalTransactionRepository extends JpaRepository<JournalTransactionEntity, String> {
    List<JournalTransactionEntity> findByPoolIdOrderByCreatedAtAsc(String poolId);
    Optional<JournalTransactionEntity> findFirstByPoolIdOrderByCreatedAtDesc(String poolId);
}
