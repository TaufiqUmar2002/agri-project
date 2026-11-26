package com.umar.riskpool.repository;

import com.umar.riskpool.domain.ManualJournalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManualJournalRepository extends JpaRepository<ManualJournalEntity, String> {
    List<ManualJournalEntity> findByPoolIdAndStatus(String poolId, String status);
}
