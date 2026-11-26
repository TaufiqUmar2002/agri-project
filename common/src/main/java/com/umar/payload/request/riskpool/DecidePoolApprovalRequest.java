package com.umar.payload.request.riskpool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class DecidePoolApprovalRequest {
    @NotBlank
    private String decision; // APPROVE | REJECT
    @NotBlank
    private String subjectHash;
    private Map<String, Boolean> checklist;
    @NotBlank
    @Size(min = 20, max = 1000)
    private String note;
}
