package com.umar.integration.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "catalog_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogSnapshotEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long snapshotVersion;

    @Column(nullable = false)
    private Instant updatedAt;
}
