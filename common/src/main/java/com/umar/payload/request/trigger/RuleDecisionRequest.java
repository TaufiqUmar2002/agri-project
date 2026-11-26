package com.umar.payload.request.trigger;

import com.umar.enums.trigger.RuleDecisionAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class RuleDecisionRequest {
    @NotNull
    private RuleDecisionAction action;
    @NotBlank
    private String definitionHash;
    private Map<String, Boolean> checklist;
    @NotBlank
    @Size(min = 10, max = 1000)
    private String comment;
}
