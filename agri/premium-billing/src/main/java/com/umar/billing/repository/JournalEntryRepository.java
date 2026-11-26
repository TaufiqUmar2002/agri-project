package com.umar.billing.repository;

import com.umar.billing.entity.JournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {
    List<JournalEntryEntity> findByReferenceOrderByPostedAtAsc(String reference);
}
