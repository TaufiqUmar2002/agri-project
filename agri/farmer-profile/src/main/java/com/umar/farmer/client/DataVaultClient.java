package com.umar.farmer.client;

import java.util.UUID;

public interface DataVaultClient {
    String tokenize(String dataClass, String value, String subjectRef, String purpose);
    String hash(String value, String purpose);
    String detokenize(String token, String purpose);
    void shredSubject(UUID userId);
}
