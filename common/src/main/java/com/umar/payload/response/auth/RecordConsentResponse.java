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
public class RecordConsentResponse {
    private List<ConsentEntry> consents;
    private List<String> missingCoreConsents;
    private String nextStep;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentEntry {
        private String consentId;
        private String purpose;
        private String textVersion;
        private String status;
        private String grantedAt;
    }
}
