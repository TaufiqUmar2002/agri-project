package com.umar.repository.keyManagement;

import javax.crypto.SecretKey;
import java.util.UUID;

public record DataKeyMaterial(SecretKey key, UUID keyVersionId) {
    public DataKeyMaterial {
        if (key == null || keyVersionId == null) {
            throw new IllegalArgumentException(
                    "Key material and version are required"
            );
        }
    }
}
