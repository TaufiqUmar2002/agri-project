package com.umar.payload.request.notification;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateNotificationPreferencesRequest {
    private String language;
    private List<String> channels;
    private Boolean whatsappOptIn;
    private Map<String, List<String>> optOut;
    private Map<String, String> quietHours;
    private String farmerId;
    private Map<String, String> onBehalfOf;
    private String supportRequestRef;
}
