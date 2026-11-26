package com.umar.payload.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterAccountResponse {
    private String challengeId;
    private String maskedPhone;
    private Integer expiresInSeconds;
    private Integer resendAfterSeconds;
    private String channel;
}
