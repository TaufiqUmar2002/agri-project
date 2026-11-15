package com.umar.entity.dataProtection;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.List;


@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(
        name = "ACCESS_POLICY",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_VAULT_ACCESS_POLICY",
                        columnNames = {
                                "SERVICE_IDENTITY",
                                "DATA_CLASS",
                                "PURPOSE",
                                "OPERATION"
                        }
                )
        }
)
public class AccessPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SERVICE_IDENTITY", nullable = false)
    private String serviceIdentity;

    @Column(name = "DATA_CLASS", nullable = false)
    private String dataClass;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "policy_purposes",
            joinColumns = @JoinColumn(name = "policy_id") // Points to AccessPolicy's Primary Key
    )
    @Column(name = "purpose")
    private List<String> allowedPurposes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "policy_operations",
            joinColumns = @JoinColumn(name = "policy_id") // Points to AccessPolicy's Primary Key
    )
    @Column(name = "operation")
    private List<String> operation;
    @Column(name = "REQUESTS_PER_MINUTE")
    private String rates;

    @Column(name = "MAX_BATCH_SIZE")
    private String batch;

    @Column(name = "CONDITIONS")
    private String conditions;

    @Column(name = "VERSIONS")
    private String versions;

    @Column(nullable = false,name = "STATUS")
    private boolean status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
