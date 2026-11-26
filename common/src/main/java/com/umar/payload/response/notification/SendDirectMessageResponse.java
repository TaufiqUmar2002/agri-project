package com.umar.payload.response.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SendDirectMessageResponse {
    private String notificationId;
    private String status;
    private String category;
    private List<String> channelPlan;
    private String expiresAt;
    private int version;
}
