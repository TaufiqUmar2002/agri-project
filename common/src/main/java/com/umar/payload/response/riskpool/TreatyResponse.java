package com.umar.payload.response.riskpool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TreatyResponse {
    private String treatyId;
    private String treatyCode;
    private int version;
    private String status;
    private String treatyHash;
    private List<Map<String, Object>> diffSummary;
    private List<Map<String, Object>> placements;
    private String approvalId;
    private List<String> approvalsRequired;
    private String effectiveFrom;
}
