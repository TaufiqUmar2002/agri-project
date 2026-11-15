package com.umar.rotation;

import java.util.UUID;

public interface KeyRotationService {

    UUID requestRotation(RotationRequest request);

    void approveRotation(UUID jobId, String custodianId);
    void startRotation(UUID jobId);
    RotationStatus getStatus(UUID jobId);
    void resumeRotation(UUID jobId);
}
