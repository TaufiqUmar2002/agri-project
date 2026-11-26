package com.umar.blindindex;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory checkpoint store for blind-index rebuild batches.
 * Suitable for a single-instance dev deployment; production should persist
 * checkpoints to the rotation-job store so rebuilds survive restarts.
 */
@Component
public class RotationCheckpointStoreImpl implements BlindIndexRotationWorker.RotationCheckpointStore {

    private final Map<UUID, String> checkpoints = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> exhausted = new ConcurrentHashMap<>();

    @Override
    public String getCheckpoint(UUID rotationJobId) {
        return checkpoints.getOrDefault(rotationJobId, null);
    }

    @Override
    public void saveCheckpoint(UUID rotationJobId, String checkpoint, int processedCount) {
        checkpoints.put(rotationJobId, checkpoint);
    }

    @Override
    public void markRebuildExhausted(UUID rotationJobId) {
        exhausted.put(rotationJobId, Boolean.TRUE);
    }

    public boolean isExhausted(UUID rotationJobId) {
        return exhausted.getOrDefault(rotationJobId, Boolean.FALSE);
    }
}