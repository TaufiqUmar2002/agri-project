package com.umar.repository.keyManagement;


import java.util.UUID;

public interface DataEncryptionKeyProvider {

    DataKeyMaterial getEncryptionKey(String subjectRef, String dataClass, String retentionGroup);

    DataKeyMaterial getDecryptionKey(UUID keyVersionId);
}
