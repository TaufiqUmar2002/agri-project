package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UseSecretResponse {
    private String ref;
    private String value;
    private int version;
    private String operation;
    private String signature;
    private Integer keyVersion;
    private String algorithm;
    private String kid;
    private Boolean valid;
    private String leaseId;
    private String credential;
    private String expiresAt;
    private Boolean renewable;
    private String targetRuntime;
    private String shownAt;
    private String requestId;
}
