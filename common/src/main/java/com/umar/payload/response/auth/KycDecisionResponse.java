package com.umar.payload.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDecisionResponse {
    private String kycId;
    private String status;
    private Boolean secondReviewPending;
    private String currentTier;
    private String decidedBy;
    private String decidedAt;
}
