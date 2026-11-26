package com.umar.payload.response.claims;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimActionResponse {
    private String claimId;
    private String status;
    private String approvalPath;
    private String route;
    private Integer attempt;
    private String retryId;
    private String nextStep;
    private List<String> holdReasons;
    private List<String> remainingApprovals;
    private String confirmedAt;
    private String decidedAt;
    private int version;
}
