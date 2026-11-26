package com.umar.payload.response.iot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceReadingsResponse {
    private String deviceId;
    private String plotId;
    private String channel;
    private String unit;
    private String resolution;
    private List<Map<String, Object>> points;
    private List<Map<String, Object>> raw;
    private List<Map<String, Object>> rejected;
    private Map<String, Object> summary;
    private String nextCursor;
}
