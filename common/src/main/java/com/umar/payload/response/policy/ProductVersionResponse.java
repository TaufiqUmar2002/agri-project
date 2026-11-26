package com.umar.payload.response.policy;

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
public class ProductVersionResponse {
    private String productId;
    private String productCode;
    private Integer version;
    private String status;
    private String definitionHash;
    private List<Map<String, Object>> diffSummary;
    private Map<String, Object> backtest;
    private List<String> approvalsRequired;
    private List<Map<String, Object>> approvals;
    private String publishesAt;
    private String effectiveFrom;
}
