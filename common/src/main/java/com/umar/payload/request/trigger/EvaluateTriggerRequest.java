package com.umar.payload.request.trigger;

import com.umar.enums.trigger.EvaluationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class EvaluateTriggerRequest {
    @NotNull
    private EvaluationType evaluationType;
    @NotBlank
    private String ruleId;
    private Integer ruleVersion;
    private List<String> regionCodes;
    private List<String> plotIds;
    @NotNull
    private LocalDate windowStart;
    @NotNull
    private LocalDate windowEnd;
    private String windowId;
    private String reason;
}
