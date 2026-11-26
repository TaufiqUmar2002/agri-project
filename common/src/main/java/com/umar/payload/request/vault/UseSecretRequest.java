package com.umar.payload.request.vault;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UseSecretRequest {

    @NotBlank(message = "Operation is required")
    private String operation; // SIGN | VERIFY | LEASE | INJECT | REVEAL_ONCE | READ

    @NotBlank(message = "Purpose is required")
    private String purpose;

    @NotBlank(message = "Context reference is required")
    private String contextRef;

    private Integer version;

    private String payload; // base64

    private String signature; // base64

    private String algorithm; // HMAC_SHA256 | RS256 | ES256

    private LeaseSpec lease;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class LeaseSpec {
        private Integer ttlSeconds;
        private List<String> scopes;
        private String target;
    }
}
