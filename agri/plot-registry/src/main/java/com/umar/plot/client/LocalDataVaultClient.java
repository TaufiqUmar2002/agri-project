package com.umar.plot.client;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LocalDataVaultClient implements DataVaultClient {
    @Override
    public String storeGpsTrack(String subjectRef, String jsonPayload) {
        return "vt_doc_gps_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Override
    public boolean verifyEvidenceToken(String token) {
        return token != null && token.startsWith("vt_doc_");
    }
}
