package com.umar.plot.client;

public interface DataVaultClient {
    String storeGpsTrack(String subjectRef, String jsonPayload);
    boolean verifyEvidenceToken(String token);
}
