package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SignResponse {
    private String signature;
    private int keyVersion;
    private String algorithm;
    private String kid;
    private String requestId;
}
