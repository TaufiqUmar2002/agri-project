package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VaultEnvelopeResponse {
    private String ciphertext;
    private String plaintext;
    private String keyVersion;
    private String dataClass;
}
