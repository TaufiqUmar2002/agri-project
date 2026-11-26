package com.umar.payload.response.claims;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResponse {
    private String disputeId;
    private String claimId;
    private String status;
    private String reasonCode;
    private String outcome;
    private String claimStatus;
    private String supplementaryClaimId;
    private Long supplementaryAmountMinor;
    private String proposalHash;
    private List<String> approvalsRequired;
    private Map<String, Object> checks;
    private String createdAt;
    private String resolvedAt;
}
