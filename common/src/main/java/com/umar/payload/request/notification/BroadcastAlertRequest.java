package com.umar.payload.request.notification;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
public class BroadcastAlertRequest {
    private String action; // CREATE | APPROVE | CANCEL
    private String alertType;
    private String severity; // INFO | WARNING | CRITICAL
    private Map<String, Object> audience;
    private String templateCode;
    private List<String> actions;
    private Map<String, Object> variables;
    private Instant scheduleAt;
    private Instant expiresAt;
    private boolean dryRun;
    private String broadcastId;
    private String proposalHash;
}
