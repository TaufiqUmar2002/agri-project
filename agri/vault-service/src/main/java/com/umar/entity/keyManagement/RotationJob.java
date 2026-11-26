package com.umar.entity.keyManagement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ROTATION_JOB")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RotationJob {

    @Id
    private UUID id;

    private String keyType;

    @Column(columnDefinition = "TEXT")
    private String scope;

    private String dataClass;

    private String secretRef;

    private String subjectRef;

    private String mode;

    private String reasonCode;

    @Column(length = 1000)
    private String note;

    private Boolean revokeOldVersion;

    private String status; // PENDING_APPROVAL | RUNNING | COMPLETED | FAILED

    private Integer oldVersion;

    private Integer newVersion;

    private Long itemsTotal = 0L;

    private Long itemsDone = 0L;

    private String checkpoint;

    private String progress;

    private UUID approvalId;

    private String approvers;

    private Instant startedAt;

    private Instant completedAt;
}
