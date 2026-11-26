package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HashResponse {
    private List<ValueHashResult> hashes;
    private String requestId;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ValueHashResult {
        private int valueIndex;
        private List<HashEntry> entries;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HashEntry {
        private String hash;
        private int keyVersion;
    }
}
