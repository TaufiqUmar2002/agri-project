package com.umar.hooks;

import com.umar.converter.MaskingRule;
import com.umar.converter.ValidationRule;
import com.umar.enums.vault.DetokenizeMode;
import com.umar.enums.vault.RETENTION_GROUP;
import com.umar.enums.vault.ValueKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mutable context passed to {@link VaultDataClassHook} contributors when a new
 * data class is being defined. Hooks may contribute or override validation and
 * masking rules before the data class is persisted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataClassDefinition {

    private String name;
    private ValueKind valueKind;
    private ValidationRule validation;
    private MaskingRule maskingRules;
    private String classification;
    private RETENTION_GROUP retentionGroup;
    private Boolean hasLookupHash;
    private String blindIndex;
    private String fpe;
    private DetokenizeMode detokenizable;
    private List<String> allowedPurposes;
    private int version;
    private String status;
}
