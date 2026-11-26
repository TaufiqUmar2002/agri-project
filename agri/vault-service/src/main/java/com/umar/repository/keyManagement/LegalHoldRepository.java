package com.umar.repository.keyManagement;

import com.umar.entity.keyManagement.LegalHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LegalHoldRepository extends JpaRepository<LegalHold, UUID> {

    List<LegalHold> findByStatusIn(List<String> statuses);

    List<LegalHold> findByStatus(String status);
}
