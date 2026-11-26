package com.umar.integration.error;

import com.umar.exception.BaseApiException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class HubException extends BaseApiException {

    public HubException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of(), null, null);
    }

    public HubException(HttpStatus status, String code, String message, Map<String, Object> details) {
        this(status, code, message, details, null, null);
    }

    public HubException(HttpStatus status, String code, String message, Map<String, Object> details,
                        List<String> errors, Long retryAfterSeconds) {
        super(status, code, message, null, null, details, errors, retryAfterSeconds);
    }

    public static HubException validation(List<String> errors) {
        return new HubException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", Map.of(), errors, null);
    }

    public static HubException validation(String error) {
        return validation(List.of(error));
    }

    public static HubException forbidden(String message) {
        return new HubException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static HubException unauthenticated(String message) {
        return new HubException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
    }

    public static HubException notFound(String code, String message) {
        return new HubException(HttpStatus.NOT_FOUND, code, message);
    }

    public static HubException conflict(String code, String message) {
        return new HubException(HttpStatus.CONFLICT, code, message);
    }

    public static HubException conflict(String code, String message, Map<String, Object> details) {
        return new HubException(HttpStatus.CONFLICT, code, message, details);
    }

    public static HubException unprocessable(String code, String message) {
        return new HubException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static HubException unprocessable(String code, String message, Map<String, Object> details) {
        return new HubException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, details);
    }

    public static HubException rateLimited(long retryAfterSeconds, String message) {
        return new HubException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message, Map.of(), null, retryAfterSeconds);
    }
}
