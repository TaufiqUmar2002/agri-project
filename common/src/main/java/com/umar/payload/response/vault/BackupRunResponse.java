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
public class BackupRunResponse {
    private UUID id;
    private String runId;
    private String type;
    private String status;
    private String backupSetIds;
    private String findings;
    private List<Map<String, Object>> backups;
    private Map<String, Object> measured;
    private List<Map<String, Object>> tests;
    private List<Map<String, Object>> findingDetails;
    private String destroyedAt;
    private String startedAt;
    private String completedAt;
}
