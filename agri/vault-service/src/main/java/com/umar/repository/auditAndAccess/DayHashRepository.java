package com.umar.repository.auditAndAccess;

import com.umar.entity.auditAndAccess.DayHash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DayHashRepository extends JpaRepository<DayHash, UUID> {

    Optional<DayHash> findByDate(String date);

    List<DayHash> findAllByOrderByDateAsc();

    Optional<DayHash> findFirstByOrderByDateDesc();
}
