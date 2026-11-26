package com.umar.payload.request.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class EraseSubjectRequest {
    private String requestId;
    private List<String> groups;
    private String reason; // DATA_SUBJECT_REQUEST | ACCOUNT_CLOSURE | RETENTION_EXPIRY | REGULATOR_ORDER
    private Boolean dryRun;
}
