package com.umar.payload.response.payout;

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
public class PayoutDetailResponse {
    private String payoutId;
    private String claimId;
    private int attempt;
    private String idempotencyKey;
    private String status;
    private boolean escalated;
    private boolean cancelRequested;
    private String route;
    private String connection;
    private long amountMinor;
    private String currency;
    private String destinationMask;
    private String providerRef;
    private String providerRefMasked;
    private List<Map<String, Object>> attempts;
    private String reasonCode;
    private Boolean retryable;
    private String holdReason;
    private Map<String, Object> cashOut;
    private Map<String, Object> statementMatch;
    private String nextStep;
    private String nextCheckAt;
    private String createdAt;
    private String initiatedAt;
    private String completedAt;
    private Double hoursToResult;
    private int version;
}
