package com.umar.payload.request.trigger;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class RunSimulationRequest {
    private String ruleId;
    private Integer ruleVersion;
    private Map<String, Object> draftRule;
    @NotEmpty
    private List<String> regionCodes;
    @NotNull
    private LocalDate fromDate;
    @NotNull
    private LocalDate toDate;
    @Min(1)
    private Integer seasons;
    private String cropCode;
    private List<Map<String, Object>> compareTo;
    private Map<String, Object> sensitivity;
    @NotBlank
    private String outputFormat; // SUMMARY | DETAILED
}
