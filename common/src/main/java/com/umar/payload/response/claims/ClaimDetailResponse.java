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
public class ClaimDetailResponse {
    private String claimId;
    private String claimNumber;
    private String type;
    private String policyId;
    private String farmerId;
    private String plotId;
    private String decisionId;
    private String status;
    private String approvalPath;
    private long calculatedMinor;
    private long payableMinor;
    private String currency;
    private String termsHash;
    private String claimHash;
    private List<String> holdReasons;
    private String nextStep;
    private int currentAttempt;
    private boolean cancelRequested;
    private Double hoursToPay;
    private List<Map<String, Object>> timeline;
    private List<Map<String, Object>> disputes;
    private List<Map<String, Object>> approvals;
    private Map<String, Object> decision;
    private Map<String, Object> payout;
    private String openedAt;
    private String paidAt;
    private String closedAt;
    private String disputeWindowEnds;
    private int version;
}
