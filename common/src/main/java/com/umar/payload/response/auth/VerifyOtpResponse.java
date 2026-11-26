package com.umar.payload.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpResponse {
    private String purpose;
    private String userId;
    private String status;
    private String kycTier;
    private String accessToken;
    private String refreshToken;
    private Integer expiresIn;
    private String tokenType;
    private String delegationId;
    private String delegationStatus;
    private String validUntil;
    private String stepUpToken;
    private Integer stepUpExpiresIn;
}
