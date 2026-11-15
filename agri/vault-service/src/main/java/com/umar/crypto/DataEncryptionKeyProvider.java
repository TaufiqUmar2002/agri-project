package com.umar.crypto;


import javax.crypto.SecretKey;
import java.util.UUID;

public interface DataEncryptionKeyProvider {

    KeyMaterial getEncryptionKey(String subjectRef, String dataClass);

    KeyMaterial getDecryptionKey(UUID keyVersionId);

    record KeyMaterial(SecretKey key, UUID keyVersionId) {
        public KeyMaterial {
            if (key == null || keyVersionId == null) {
                throw new IllegalArgumentException("Key and key version are required");
            }
        }
    }
}
