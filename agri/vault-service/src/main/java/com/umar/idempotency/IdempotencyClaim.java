package com.umar.idempotency;


import java.util.UUID;

public record IdempotencyClaim(
        ClaimType type,
        UUID recordId,
        String responsePayload,
        Integer httpStatus) {

    public enum ClaimType {
        NEW_REQUEST,
        REPLAY,
        IN_PROGRESS,
        PAYLOAD_CONFLICT
    }

    public static IdempotencyClaim newRequest(UUID id) {
        return new IdempotencyClaim(
                ClaimType.NEW_REQUEST, id, null, null);
    }

    public static IdempotencyClaim replay(
            UUID id,
            String response,
            Integer status) {

        return new IdempotencyClaim(
                ClaimType.REPLAY, id, response, status);
    }

    public static IdempotencyClaim inProgress(UUID id) {
        return new IdempotencyClaim(
                ClaimType.IN_PROGRESS, id, null, null);
    }

    public static IdempotencyClaim conflict(UUID id) {
        return new IdempotencyClaim(
                ClaimType.PAYLOAD_CONFLICT, id, null, null);
    }
}
