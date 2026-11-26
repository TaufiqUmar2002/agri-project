package com.umar.payload.request.trigger;

import com.umar.enums.trigger.ReviewDecisionAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReviewTriggerDecisionRequest {
    @NotNull
    private ReviewDecisionAction action;
    @NotBlank
    @Size(min = 20, max = 2000)
    private String note;
    private List<String> evidenceRefs;
    private Integer applyToAffectedCohortVersion;
    private String confirmationCode;
}
