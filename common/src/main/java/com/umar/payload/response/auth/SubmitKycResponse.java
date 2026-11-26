package com.umar.payload.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitKycResponse {
    private String kycId;
    private String status;
    private String targetTier;
    private String submittedAt;
    private String estimatedDecisionBy;
}
