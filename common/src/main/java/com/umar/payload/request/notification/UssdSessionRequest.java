package com.umar.payload.request.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UssdSessionRequest {
    @NotBlank
    private String sessionId;
    @NotBlank
    private String serviceCode;
    @NotBlank
    private String msisdn;
    private String networkCode;
    private String input = "";
}
