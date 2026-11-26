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
public class FarmerSearchResponse {
    private List<FarmerSummary> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmerSummary {
        private String farmerId;
        private String displayName;
        private String region;
        private String groupName;
        private String status;
    }
}
