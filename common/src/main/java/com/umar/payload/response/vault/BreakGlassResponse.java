package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BreakGlassResponse {
    private UUID id;
    private String requestId;
    private String incidentRef;
    private String scope;
    private String investigatorRef;
    private String status;
    private String validUntil;
    private Integer durationMinutes;
    private String approvalId;
    private List<String> approvalsRequired;
    private Map<String, Object> grant;
    private String reviewDueBy;
}
