package com.umar.idempotency;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class IdempotencyControllerAdvice {

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            IdempotencyConflictException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "IDEMPOTENCY_KEY_REUSED",
                        ex.getMessage(),
                        Instant.now()));
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<ErrorResponse> handleInProgress(
            IdempotencyInProgressException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", "2")
                .body(new ErrorResponse(
                        "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                        ex.getMessage(),
                        Instant.now()));
    }

    public record ErrorResponse(
            String code,
            String message,
            Instant timestamp) {
    }

    public static class IdempotencyConflictException
            extends RuntimeException {

        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    public static class IdempotencyInProgressException
            extends RuntimeException {

        public IdempotencyInProgressException(String message) {
            super(message);
        }
    }
}
