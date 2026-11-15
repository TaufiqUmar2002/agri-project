package com.umar.rotation;

import java.util.UUID;

public interface KeyRotationWorker {

    void rotateClassKek(UUID jobId, String dataClass);

    void rotateSubjectDek(UUID jobId, String subjectRef, String retentionGroup);

    void verifyRotation(UUID jobId);

    void retireOldVersion(UUID jobId);
}
