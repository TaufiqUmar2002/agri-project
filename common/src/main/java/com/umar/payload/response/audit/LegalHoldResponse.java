package com.umar.payload.response.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LegalHoldResponse {
    private String holdId;
    private String status;
    private String caseRef;
    private String reasonCode;
    private Map<String, Object> scope;
    private Map<String, Object> resolved;
    private List<String> approvalsRequired;
    private String proposalHash;
    private int version;
}
