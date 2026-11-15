package com.umar.payload.response.vault;

public record ItemFailure(
        int index,
        String code,
        String message
) {}
