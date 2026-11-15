package com.umar.rotation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RotationStatus(

        UUID jobId,

        RotationRequest.KeyType keyType,

        RotationRequest.RotationScope scope,

        RotationRequest.RotationMode mode,

        RotationState status,

        Integer oldVersion,

        Integer newVersion,

        Progress progress,

        UUID approvalId,

        ApprovalRequirement approvalsRequired,

        Instant estimatedCompletion

) {

    public enum RotationState {
        PENDING_APPROVAL,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record Progress(
            long itemsTotal,
            long itemsDone,
            String checkpoint
    ) {
        public Progress {
            if (itemsTotal < 0 || itemsDone < 0) {
                throw new IllegalArgumentException(
                        "Progress values cannot be negative"
                );
            }

            if (itemsTotal > 0 && itemsDone > itemsTotal) {
                throw new IllegalArgumentException(
                        "itemsDone cannot exceed itemsTotal"
                );
            }
        }

        public double percentage() {
            if (itemsTotal == 0) {
                return 0.0;
            }

            return (itemsDone * 100.0) / itemsTotal;
        }
    }

    public record ApprovalRequirement(
            int requiredCount,
            List<String> requiredRoles
    ) {
        public ApprovalRequirement {
            requiredRoles = requiredRoles == null
                    ? List.of()
                    : List.copyOf(requiredRoles);
        }
    }
}
