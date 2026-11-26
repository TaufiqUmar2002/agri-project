package com.umar.exception;

import com.umar.audit.AuditException;
import com.umar.idempotency.IdempotencyControllerAdvice;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Vault-specific ExceptionHook plugged into common's centralized GlobalExceptionHandler.
 */
@Component
public class VaultExceptionHook implements ExceptionHook {

    @Override
    public boolean supports(Throwable ex) {
        return ex instanceof VaultPolicyDeniedException
                || ex instanceof PolicyViolationException
                || ex instanceof AuditException
                || ex instanceof SecurityException
                || ex instanceof IdempotencyControllerAdvice.IdempotencyConflictException
                || ex instanceof IdempotencyControllerAdvice.IdempotencyInProgressException;
    }

    @Override
    public ResponseEntity<Map<String, Object>> handle(Throwable ex, MessageSource messageSource, Locale locale) {
        if (ex instanceof VaultPolicyDeniedException) {
            return build(HttpStatus.FORBIDDEN, "POLICY_DENIED", "Not authorised for this purpose: " + ex.getMessage(), null);
        }
        if (ex instanceof PolicyViolationException) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Not authorised";
            String code = msg.toLowerCase().contains("own request") ? "REQUESTER_CANNOT_APPROVE" : "FORBIDDEN";
            return build(HttpStatus.FORBIDDEN, code, msg, null);
        }
        if (ex instanceof AuditException) {
            return build(HttpStatus.SERVICE_UNAVAILABLE, "ACCESS_LOG_UNAVAILABLE", "Try again shortly", null);
        }
        if (ex instanceof SecurityException) {
            return build(HttpStatus.SERVICE_UNAVAILABLE, "KEY_SERVICE_UNAVAILABLE", "Try again shortly", null);
        }
        if (ex instanceof IdempotencyControllerAdvice.IdempotencyConflictException) {
            return build(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", ex.getMessage(), null);
        }
        if (ex instanceof IdempotencyControllerAdvice.IdempotencyInProgressException) {
            return build(HttpStatus.CONFLICT, "IDEMPOTENCY_REQUEST_IN_PROGRESS", ex.getMessage(), "2");
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message, String retryAfter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("error", message);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        ResponseEntity.BodyBuilder b = ResponseEntity.status(status);
        if (retryAfter != null) {
            b.header(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        return b.body(body);
    }
}
