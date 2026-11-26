package com.umar.payload.response.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstalmentPlanResponse {
    private String planId;
    private String planCode;
    private Integer version;
    private String status;
    private String planHash;
    private List<Map<String, Object>> diffSummary;
    private String approvalId;
    private List<String> approvalsRequired;
    private String effectiveFrom;
}
