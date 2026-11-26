package com.umar.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Domain exception carrying the HTTP status, Appendix A error code, and optional structured details.
 */
public class VaultApiException extends BaseApiException {

    public VaultApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public VaultApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }

    public static VaultApiException badRequest(String code, String message) {
        return new VaultApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static VaultApiException unauthorized(String code, String message) {
        return new VaultApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static VaultApiException forbidden(String code, String message) {
        return new VaultApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static VaultApiException notFound(String code, String message) {
        return new VaultApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static VaultApiException conflict(String code, String message) {
        return new VaultApiException(HttpStatus.CONFLICT, code, message);
    }

    public static VaultApiException conflict(String code, String message, Map<String, Object> details) {
        return new VaultApiException(HttpStatus.CONFLICT, code, message, details);
    }

    public static VaultApiException gone(String code, String message) {
        return new VaultApiException(HttpStatus.GONE, code, message);
    }

    public static VaultApiException payloadTooLarge(String code, String message, Map<String, Object> details) {
        return new VaultApiException(HttpStatus.PAYLOAD_TOO_LARGE, code, message, details);
    }

    public static VaultApiException unprocessable(String code, String message) {
        return new VaultApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static VaultApiException unprocessable(String code, String message, Map<String, Object> details) {
        return new VaultApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, details);
    }

    public static VaultApiException rateLimited(String message, long retryAfterSeconds) {
        return new VaultApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message,
                Map.of("retryAfterSeconds", retryAfterSeconds));
    }

    public static VaultApiException unavailable(String code, String message) {
        return new VaultApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
