package com.umar.payload.request.vault;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ApprovalDecisionRequest {

    @NotBlank(message = "Decision is required")
    @Pattern(regexp = "APPROVE|REJECT", message = "Decision must be APPROVE or REJECT")
    private String decision;

    private String approverRole;

    private String subjectHash;

    private Map<String, Boolean> checklist;

    private String note;

    private String comment;
}
