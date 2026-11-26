package com.umar.payload.request.policy;

import com.umar.enums.policy.ActivationTrigger;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivatePolicyRequest {
    @NotNull
    private ActivationTrigger trigger;
    private String reasonCode; // RECEIPT_VERIFIED | HOLD_RESOLVED | UNDERWRITER_OVERRIDE
    private String note;
}
