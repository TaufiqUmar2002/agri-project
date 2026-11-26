package com.umar.repository.auditAndAccess;

import com.umar.entity.auditAndAccess.BreakGlassRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BreakGlassRequestRepository extends JpaRepository<BreakGlassRequest, UUID> {

    long countByRequestedByAndCreatedAtAfter(String requestedBy, Instant after);

    List<BreakGlassRequest> findByStatus(String status);
}
