package com.umar.payload.request.farmer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterWalletRequest {

    @NotBlank(message = "provider is required")
    private String provider;

    @NotBlank(message = "walletNumber is required")
    private String walletNumber;

    @NotBlank(message = "accountHolderName is required")
    @Size(min = 2, max = 120, message = "accountHolderName must be 2 to 120 characters")
    private String accountHolderName;

    private Boolean makePrimary;
    private String deviceId;
}
