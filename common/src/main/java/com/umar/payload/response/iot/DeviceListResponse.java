package com.umar.payload.response.iot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceListResponse {
    private List<Map<String, Object>> items;
    private Map<String, Long> counts;
    private Map<String, Object> fleet;
    private String nextCursor;
}
