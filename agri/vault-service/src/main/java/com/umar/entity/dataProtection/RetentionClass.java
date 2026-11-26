package com.umar.entity.dataProtection;

import com.umar.enums.vault.LegalBasis;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "RETENTION_CLASS")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RetentionClass {

    @Id
    private UUID id;

    @Column(name = "CLASS_NAME", unique = true)
    private String name;

    @Column(name = "GROUP_NAME")
    private String group;

    @Column(name = "PERIOD_DAYS")
    private Integer periodDays;

    @Column(name = "MINIMUM_DAYS")
    private Integer minimumDays;

    @Column(name = "LEGAL_BASIS")
    @Enumerated(EnumType.STRING)
    private LegalBasis legalBasis;

    @Column(name = "STATUS")
    @Builder.Default
    private String status = "ACTIVE";
}
