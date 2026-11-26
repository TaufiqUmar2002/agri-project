package com.umar.payload.response.riskpool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ManualJournalResponse {
    private String journalId;
    private String poolId;
    private String status;
    private String journalType;
    private String valueDate;
    private long totalMinor;
    private String currency;
    private String approvalId;
    private List<String> approvalsRequired;
    private List<String> holdReasons;
    private String createdAt;
}
