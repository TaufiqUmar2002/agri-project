package com.umar.payload.request.claims;

import com.umar.enums.claims.ClaimDecisionAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApproveClaimRequest {
    @NotNull
    private ClaimDecisionAction action;
    @NotBlank
    private String claimHash;
    private String retryId;
    @NotBlank
    @Size(min = 20, max = 1000)
    private String note;
    private Boolean fraudCleared;
}
