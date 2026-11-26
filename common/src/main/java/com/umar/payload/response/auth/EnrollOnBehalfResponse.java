package com.umar.payload.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollOnBehalfResponse {
    private String delegationId;
    private String status;
    private String challengeId;
    private String maskedPhone;
    private String confirmationExpiresAt;
    private List<String> scopes;
    private Integer validityHours;
}
