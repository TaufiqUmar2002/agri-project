package com.umar.payload.request.vault;

import com.umar.enums.vault.DetokenizeMode;
import com.umar.enums.vault.RETENTION_GROUP;
import com.umar.enums.vault.ValueKind;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DefineDataClassRequest {

    @NotBlank(message = "Data class name is required")
    private String name;

    private ValueKind valueKind;

    private String classification;

    private RETENTION_GROUP retentionGroup;

    private Boolean hasLookupHash;

    private String blindIndex;

    private String fpe;

    private DetokenizeMode detokenizable;

    private List<String> allowedPurposes;

    private Integer version;

    private String status;
}
