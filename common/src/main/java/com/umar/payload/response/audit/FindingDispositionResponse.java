package com.umar.payload.response.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FindingDispositionResponse {
    private String findingId;
    private String status;
    private String action;
    private String approvalStatus;
    private List<String> approvalsRequired;
    private String proposalHash;
    private String acceptUntil;
    private List<Map<String, Object>> history;
    private int version;
}
