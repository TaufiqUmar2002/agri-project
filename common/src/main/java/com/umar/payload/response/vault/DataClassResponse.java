package com.umar.payload.response.vault;

import com.umar.enums.vault.DetokenizeMode;
import com.umar.enums.vault.RETENTION_GROUP;
import com.umar.enums.vault.ValueKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataClassResponse {
    private UUID id;
    private String name;
    private ValueKind valueKind;
    private String classification;
    private RETENTION_GROUP retentionGroup;
    private DetokenizeMode detokenizable;
    private List<String> allowedPurposes;
    private int version;
    private String status;
}
