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
public class VerifyBackupRequest {

    private String type; // VERIFY | RESTORE_DRILL

    private String backupSetId;

    private String backupSetIds;

    private List<String> regions;

    private String environment; // ISOLATED_DRILL

    private Targets targets;

    private String testSet; // STANDARD | EXTENDED

    private String note;

    private String findings;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class Targets {
        private Integer rpoMinutes;
        private Integer rtoMinutes;
    }
}
