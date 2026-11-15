package com.umar.crypto;


public record EncryptionContext(String dataClass, String subjectRef, String token) {

    public EncryptionContext {
        if (isBlank(dataClass)) {
            throw new IllegalArgumentException("dataClass is required");
        }
        if (isBlank(subjectRef)) {
            throw new IllegalArgumentException("subjectRef is required");
        }
        if (isBlank(token)) {
            throw new IllegalArgumentException("token is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
