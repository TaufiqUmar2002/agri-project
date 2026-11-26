package com.umar.policy.entity;

import com.umar.enums.policy.PerilType;
import com.umar.enums.policy.ProductVersionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "product_versions",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_version", columnNames = {"product_id", "version_num"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVersionEntity {
    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "product_id", nullable = false, length = 40)
    private String productId;

    @Column(name = "product_code", nullable = false, length = 40)
    private String productCode;

    @Column(name = "version_num", nullable = false)
    private Integer versionNum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ProductVersionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "peril", nullable = false, length = 32)
    private PerilType peril;

    @Column(name = "definition_json", nullable = false, columnDefinition = "TEXT")
    private String definitionJson;

    @Column(name = "definition_hash", nullable = false, length = 64)
    private String definitionHash;

    @Column(name = "diff_json", columnDefinition = "TEXT")
    private String diffJson;

    @Column(name = "backtest_ref", length = 64)
    private String backtestRef;

    @Column(name = "backtest_json", columnDefinition = "TEXT")
    private String backtestJson;

    @Column(name = "author_id", nullable = false, length = 64)
    private String authorId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "successor_product_id", length = 40)
    private String successorProductId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
