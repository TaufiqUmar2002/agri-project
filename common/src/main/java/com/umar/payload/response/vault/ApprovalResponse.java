package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovalResponse {
    private UUID id;
    private String approvalId;
    private String type;
    private String subjectId;
    private String subjectHash;
    private String status;
    private String decision;
    private String decidedBy;
    private Instant decidedAt;
    private List<String> decisions;
    private List<String> remaining;
    private String subjectStatus;
    private int requiredApprovals;
    private int approvalCount;
}
