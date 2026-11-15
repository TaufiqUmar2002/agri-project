package com.umar.repository.keyManagement;

import java.util.UUID;

public interface SubjectKeyService {

    DataKeyMaterial getOrCreateActiveKey(String subjectRef, String dataClass, String retentionGroup);

    DataKeyMaterial getKeyByVersion(UUID keyVersionId);

    void shredSubjectKey(String subjectRef, String retentionGroup);
}
