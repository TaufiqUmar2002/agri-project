package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalHoldResponse {
    private UUID id;
    private String holdId;
    private String caseRef;
    private String authority;
    private String scope;
    private Map<String, Object> scopeSummary;
    private String status;
    private String reviewDate;
    private String approvalId;
    private String createdAt;
    private String requestId;
}
