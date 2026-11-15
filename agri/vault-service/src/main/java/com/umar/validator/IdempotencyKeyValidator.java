package com.umar.validator;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IdempotencyKeyValidator {

    public UUID validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        try {
            return UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid idempotency key format", e);
        }
    }
}
