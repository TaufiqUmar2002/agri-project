package com.umar.repository.governance;

import com.umar.entity.governance.BackupRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BackupRunRepository extends JpaRepository<BackupRun, UUID> {
}
