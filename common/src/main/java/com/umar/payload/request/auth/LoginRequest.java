package com.umar.payload.request.auth;

import com.umar.enums.auth.GrantType;
import com.umar.enums.auth.RegistrationChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotNull(message = "grantType is required")
    private GrantType grantType;

    private String phoneNumber;

    @Size(max = 80, message = "username cannot exceed 80 characters")
    private String username;

    private String otp;
    private String challengeId;
    private String pin;
    private String password;
    private String mfaCode;

    @Size(max = 128, message = "deviceId cannot exceed 128 characters")
    private String deviceId;

    private RegistrationChannel channel;
}
