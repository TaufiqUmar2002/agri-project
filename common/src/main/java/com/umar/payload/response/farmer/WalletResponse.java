package com.umar.payload.response.farmer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private String walletId;
    private String provider;
    private String maskedNumber;
    private String status;
    private Boolean isPrimary;
    private String effectiveFrom;
    private String coolingUntil;
    private String reasonCode;
    private String nextAction;
}
