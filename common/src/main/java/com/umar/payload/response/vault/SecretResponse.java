package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SecretResponse {
    private UUID id;
    private String ref;
    private String name;
    private String owner;
    private String status;
    private int version;
    private String secretClass;
    private String state;
    private Map<String, Object> publicKey;
    private String activeFrom;
    private String previousVersionRetiresAt;
    private Map<String, Object> rotation;
    private String revealOnce;
    private String requestId;
}
