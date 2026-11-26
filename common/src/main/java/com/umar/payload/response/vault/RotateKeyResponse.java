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
public class RotateKeyResponse {
    private UUID id;
    private String jobId;
    private String keyType;
    private String scope;
    private String mode;
    private String status;
    private Integer newVersion;
    private Integer oldVersion;
    private Map<String, Object> progress;
    private String approvalId;
    private List<String> approvalsRequired;
    private String estimatedCompletion;
    private String requestId;
}
