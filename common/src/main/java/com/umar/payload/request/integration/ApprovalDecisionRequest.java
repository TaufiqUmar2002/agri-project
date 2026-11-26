package com.umar.payload.request.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ApprovalDecisionRequest {
    @NotBlank
    private String decision;
    @NotBlank
    private String subjectHash;
    private Map<String, Boolean> checklist;
    @NotBlank @Size(min = 20, max = 1000)
    private String note;
}
