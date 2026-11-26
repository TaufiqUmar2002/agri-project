package com.umar.repository.events;

import com.umar.events.VaultEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VaultEventOutboxRepository extends JpaRepository<VaultEventOutbox, UUID> {

    List<VaultEventOutbox> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
