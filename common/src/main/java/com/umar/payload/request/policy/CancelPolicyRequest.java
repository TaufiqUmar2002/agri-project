package com.umar.payload.request.policy;

import com.umar.enums.policy.CancelReasonCode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CancelPolicyRequest {
    @NotNull
    private CancelReasonCode reasonCode;
    private String note;
}
