package com.umar.payload.response.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BroadcastResponse {
    private String broadcastId;
    private String status;
    private String alertType;
    private String severity;
    private Map<String, Object> audience;
    private List<String> approvalsRequired;
    private String proposalHash;
    private String scheduleAt;
    private String expiresAt;
    private Map<String, Object> costEstimate;
    private Map<String, Object> progress;
}
