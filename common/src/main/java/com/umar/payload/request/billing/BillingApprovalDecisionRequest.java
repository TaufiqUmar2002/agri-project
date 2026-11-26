package com.umar.payload.request.billing;

import com.umar.enums.billing.ApprovalDecisionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class BillingApprovalDecisionRequest {
    @NotNull
    private ApprovalDecisionType decision;
    @NotBlank
    private String subjectHash;
    private Map<String, Boolean> checklist;
    @NotBlank
    @Size(min = 20, max = 1000)
    private String note;
}
