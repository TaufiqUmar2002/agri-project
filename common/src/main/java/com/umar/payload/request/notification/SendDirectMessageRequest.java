package com.umar.payload.request.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class SendDirectMessageRequest {
    @NotNull
    private Map<String, String> recipient; // type, ref
    @NotBlank
    private String templateCode;
    private Map<String, Object> variables;
    private String secret;
    private Integer expiresInSeconds;
    private String channelHint;
    @NotBlank
    private String sourceRef;
}
