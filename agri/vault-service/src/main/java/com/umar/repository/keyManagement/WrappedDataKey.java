package com.umar.repository.keyManagement;


import java.util.UUID;

public record WrappedDataKey(byte[] wrappedKey, UUID classKekVersionId) {
    public WrappedDataKey {
        if (wrappedKey == null || wrappedKey.length == 0) {
            throw new IllegalArgumentException("Wrapped key is required");
        }
        if (classKekVersionId == null) {
            throw new IllegalArgumentException("KEK version is required");
        }
        wrappedKey = wrappedKey.clone();
    }
    @Override
    public byte[] wrappedKey() {
        return wrappedKey.clone();
    }
}
