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
public class WithdrawConsentResponse {
    private String consentId;
    private String purpose;
    private String status;
    private String withdrawnAt;
    private List<String> effects;
}
