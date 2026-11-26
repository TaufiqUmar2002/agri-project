package com.umar.payload.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusResponse {
    private String kycId;
    private String status;
    private String targetTier;
    private String currentTier;
    private List<String> reasonCodes;
    private String nextAction;
    private String estimatedDecisionBy;
    private StaffView staff;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffView {
        private BigDecimal matchScore;
        private String providerRef;
        private Set<String> flags;
        private String reviewerId;
        private String secondReviewerId;
        private Integer queuePosition;
    }
}
