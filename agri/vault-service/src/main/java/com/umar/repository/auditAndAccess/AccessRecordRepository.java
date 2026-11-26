package com.umar.repository.auditAndAccess;

import com.umar.entity.auditAndAccess.AccessRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessRecordRepository extends JpaRepository<AccessRecord, UUID> {

    List<AccessRecord> findAllByOrderByTimestampAsc();

    Optional<AccessRecord> findFirstByOrderByTimestampDesc();

    List<AccessRecord> findByDayDateOrderByTimestampAsc(String dayDate);

    List<AccessRecord> findBySubjectHashOrderByTimestampDesc(String subjectHash);
}
