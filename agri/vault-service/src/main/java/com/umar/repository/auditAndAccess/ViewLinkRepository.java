package com.umar.repository.auditAndAccess;

import com.umar.entity.auditAndAccess.ViewLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ViewLinkRepository extends JpaRepository<ViewLink, UUID> {
}
