package com.umar.entity.governance;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "BACKUP_RUN")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BackupRun {

    @Id
    private UUID id;

    private String  type;

    private String backupSetIds;

    private String rpoAchieved;

    private String rtoAchieved;

    private String findings;

    private String destroyedAt;
}
