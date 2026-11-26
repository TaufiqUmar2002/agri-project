package com.umar.auth.client;

import java.util.List;
import java.util.UUID;

public interface DataVaultClient {
    String tokenize(String dataClass, String value, String subjectRef, String purpose);
    String computeKeyedHash(String value, String purpose);
    String storeDocument(String dataClass, String subjectRef, String purpose, String filename, byte[] bytes);
    boolean verifyDocumentExists(String documentToken);
    void shredSubject(UUID userId, String reason);
    List<String> listActiveLegalHolds(UUID userId);
}
