package com.umar.payload.request.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CreatePolicyRequest {

    // Legacy direct fields
    private String serviceIdentity;
    private String dataClass;
    private List<String> purposes;
    private List<String> operations;
    private Integer requestsPerMinute;
    private Integer maxBatchSize;
    private String conditions;

    // VLT-12 unified policy fields
    private String kind; // DATA_CLASS | RETENTION_CLASS | ACCESS_POLICY
    private String name;
    private Integer baseVersion;
    private Map<String, Object> definition;
    private String effectiveFrom;
    private String reason;
}
