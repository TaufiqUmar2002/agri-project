package com.umar.payload.response.farmer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteGroupMembersResponse {
    private int accepted;
    private int skipped;
    private int rejected;
    private List<RowResult> results;
    private String expiresAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowResult {
        private int index;
        private String status; // INVITED | SKIPPED | REJECTED
        private String reasonCode;
    }
}
