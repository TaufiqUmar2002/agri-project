package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PolicyResponse {
    private Long id;
    private String serviceIdentity;
    private String dataClass;
    private List<String> purposes;
    private List<String> operations;
    private String requestsPerMinute;
    private String maxBatchSize;
    private String conditions;
    private Boolean status;
    private Instant createdAt;

    // VLT-12 fields
    private String kind;
    private String name;
    private Integer version;
    private String policyStatus; // PENDING_APPROVAL | ACTIVE | SCHEDULED
    private String change;       // NEW | NARROWING | WIDENING
    private String definitionHash;
    private String effectiveFrom;
    private String approvalId;
    private List<String> approvalsRequired;
    private List<String> diffSummary;
}
