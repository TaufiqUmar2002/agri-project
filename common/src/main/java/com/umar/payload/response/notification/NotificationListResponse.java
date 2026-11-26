package com.umar.payload.response.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationListResponse {
    private List<Map<String, Object>> items;
    private Map<String, Long> counts;
    private List<Map<String, Object>> aggregators;
    private String nextCursor;
}
