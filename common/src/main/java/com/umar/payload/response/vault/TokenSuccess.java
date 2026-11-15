package com.umar.payload.response.vault;

import java.time.Instant;

public record TokenSuccess(
        int index,
        String token,
        String dataClass,
        String masked,
        boolean reused,
        String format,
        Instant createdAt
) {}
