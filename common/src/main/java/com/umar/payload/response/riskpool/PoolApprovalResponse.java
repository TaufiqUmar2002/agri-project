package com.umar.payload.response.riskpool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PoolApprovalResponse {
    private String approvalId;
    private String type;
    private String subjectId;
    private String status;
    private List<Map<String, Object>> decisions;
    private List<String> remaining;
    private String subjectStatus;
    private String subjectHash;
}
