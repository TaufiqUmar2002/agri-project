package com.umar.entity.dataProtection;

import com.umar.converter.MaskingRule;
import com.umar.converter.MaskingRuleConverter;
import com.umar.converter.ValidationRule;
import com.umar.converter.ValidationRuleConverter;
import com.umar.enums.vault.DetokenizeMode;
import com.umar.enums.vault.RETENTION_GROUP;
import com.umar.enums.vault.ValueKind;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "DATA_CLASS")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class DataClass {

    @Id
    private UUID id;

    @Column(unique = true,name = "data_class_name")
    private String name;

    @Column(name = "value_kind")
    @Enumerated(EnumType.STRING)
    private ValueKind valueKind;

    @Convert(converter = ValidationRuleConverter.class)
    @Column(name = "validation_rules", nullable = true)
    private ValidationRule validation;

    @Convert(converter = MaskingRuleConverter.class)
    @Column(name = "masking_rules",nullable = true)
    private MaskingRule maskingRules;

    @Column(name = "classification")
    private String classification;

    @Column(name = "retention_group")
    @Enumerated(EnumType.STRING)
    private RETENTION_GROUP retentionGroup;

    @Column(name = "has_lookup_hash")
    private Boolean hasLookupHash;

    @Column(name = "blind_index")
    private String blindIndex;

    @Column(name = "fpe")
    private String fpe;

    @Column(name = "detokenizable")
    @Enumerated(EnumType.STRING)
    private DetokenizeMode detokenizable;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dataclass_purposes", joinColumns = @JoinColumn(name  = "dataclass_name"))
    private List<String> allowedPurposes;

    @Column(name = "version")
    private int version;

    @Column(name = "status")
    private String status;

}
