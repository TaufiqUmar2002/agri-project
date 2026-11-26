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
public class DocumentResponse {
    private UUID id;
    private String token;
    private String dataClass;
    private String subjectRef;
    private String contentType;
    private String sizeBytes;
    private String sha256;
    private String retentionClass;
    private String retainUntil;
    private Boolean metadataStored;
    private String createdAt;
    private String requestId;
    private Boolean legalHold;

    // VLT-14 VIEW mode fields
    private String viewUrl;
    private String expiresAt;
    private Boolean singleUse;
    private Map<String, Object> watermark;

    // VLT-14 VERIFY mode fields
    private Boolean exists;
    private Boolean intact;
    private Boolean subjectMatches;
    private Boolean hashMatches;
}
