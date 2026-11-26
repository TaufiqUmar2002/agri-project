package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EraseSubjectResponse {
    private UUID id;
    private String subjectRef;
    private String subjectHash;
    private String shreddedAt;
    private String requestId;
    private String outcome; // COMPLETED | PARTIAL | ON_HOLD
    private List<GroupOutcome> groups;
    private String verifiedAt;
    private Boolean dryRun;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GroupOutcome {
        private String name;
        private String status; // SHREDDED | RETAINED | ON_HOLD
        private String legalBasis;
        private String retainUntil;
        private int itemsAffected;
    }
}
