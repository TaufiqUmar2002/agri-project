package com.umar.payload.response.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationStatusResponse {
    private String notificationId;
    private String category;
    private String templateCode;
    private int templateVersion;
    private String status;
    private String language;
    private boolean languageFallback;
    private List<String> channelPlan;
    private String currentChannel;
    private String deferredUntil;
    private String suppressionReason;
    private List<Map<String, Object>> attempts;
    private List<String> failover;
    private String reasonCode;
    private Map<String, Object> content;
    private String createdAt;
    private String sentAt;
    private String deliveredAt;
    private String expiresAt;
    private int version;
}
