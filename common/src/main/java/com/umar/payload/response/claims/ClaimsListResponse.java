package com.umar.payload.response.claims;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimsListResponse {
    private List<Map<String, Object>> items;
    private Map<String, Long> counts;
    private int page;
    private int size;
    private long total;
}
