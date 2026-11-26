package com.umar.blindindex;


import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class BlindIndexRotationWorker {

    private final BlindIndexService blindIndexService;
    private final BlindIndexRebuildSource rebuildSource;
    private final RotationCheckpointStore checkpointStore;
    private final BlindIndexRepository repository;

    public BlindIndexRotationWorker(
            BlindIndexService blindIndexService,
            BlindIndexRebuildSource rebuildSource,
            RotationCheckpointStore checkpointStore,
            BlindIndexRepository repository) {

        this.blindIndexService = blindIndexService;
        this.rebuildSource = rebuildSource;
        this.checkpointStore = checkpointStore;
        this.repository = repository;
    }

    /**
     * Processes one batch and persists its checkpoint.
     *
     * A scheduler or rotation-job worker calls this repeatedly.
     */
    public BatchResult rebuildBatch(
            UUID rotationJobId,
            String dataClass,
            UUID newIndexKeyVersionId,
            int batchSize) {

        String checkpoint =
                checkpointStore.getCheckpoint(rotationJobId);

        List<RebuildItem> items =
                rebuildSource.fetchNextBatch(
                        dataClass,
                        checkpoint,
                        batchSize);

        if (items.isEmpty()) {
            checkpointStore.markRebuildExhausted(rotationJobId);
            return new BatchResult(0, true, checkpoint);
        }

        String lastCheckpoint = checkpoint;

        for (RebuildItem item : items) {

            // The source must decrypt inside the trusted Vault boundary.
            // It must not send plaintext to another service.
            String canonicalValue =
                    rebuildSource.decryptAndCanonicalize(item);

            blindIndexService.saveIndexForVersion(
                    item.dataClass(),
                    item.subjectRef(),
                    item.tokenId(),
                    newIndexKeyVersionId,
                    canonicalValue);

            lastCheckpoint = item.cursor();
        }

        // Checkpoint only after every item in this batch was processed.
        checkpointStore.saveCheckpoint(
                rotationJobId,
                lastCheckpoint,
                items.size());

        return new BatchResult(
                items.size(),
                false,
                lastCheckpoint);
    }

    /**
     * Verifies that every eligible token has a new-version index.
     *
     * This is a coverage check, not by itself proof that every hash is
     * mathematically correct. Sample lookups must also be verified.
     */
    public VerificationResult verifyCoverage(
            String dataClass,
            UUID newIndexKeyVersionId) {

        long expected =
                rebuildSource.countEligibleTokens(dataClass);

        long actual =
                repository.countForVersion(
                        dataClass,
                        newIndexKeyVersionId);

        return new VerificationResult(
                expected,
                actual,
                expected == actual);
    }

    public record RebuildItem(
            UUID tokenId,
            String dataClass,
            String subjectRef,
            String cursor) {
    }

    public record BatchResult(
            int processed,
            boolean exhausted,
            String checkpoint) {
    }

    public record VerificationResult(
            long expected,
            long actual,
            boolean complete) {
    }

    /**
     * Adapter to the existing token repository and CryptoService.
     */
    public interface BlindIndexRebuildSource {

        List<RebuildItem> fetchNextBatch(
                String dataClass,
                String afterCursor,
                int batchSize);

        String decryptAndCanonicalize(RebuildItem item);

        long countEligibleTokens(String dataClass);
    }

    /**
     * Adapter to the rotation-job/checkpoint persistence layer.
     */
    public interface RotationCheckpointStore {

        String getCheckpoint(UUID rotationJobId);

        void saveCheckpoint(
                UUID rotationJobId,
                String checkpoint,
                int processedCount);

        void markRebuildExhausted(UUID rotationJobId);
    }
}