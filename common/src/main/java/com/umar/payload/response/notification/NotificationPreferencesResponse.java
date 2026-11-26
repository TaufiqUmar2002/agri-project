package com.umar.payload.response.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationPreferencesResponse {
    private String farmerId;
    private Boolean isDefault;
    private String language;
    private List<String> channels;
    private Map<String, Object> optOut;
    private Map<String, Object> quietHours;
    private List<String> summary;
    private List<Map<String, Object>> consent;
    private List<Map<String, Object>> history;
    private int version;
    private String updatedAt;
    private String updatedBy;
}
