package com.umar.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared base API exception across all AgreeShield microservices.
 * Carries HTTP status, machine-readable error code, optional MessageSource key/args,
 * optional validation error list, retry-after metadata, and structured details.
 */
@Getter
public class BaseApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final Object[] messageArgs;
    private final Map<String, Object> details;
    private final List<String> errors;
    private final Long retryAfterSeconds;

    public BaseApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null, Collections.emptyMap(), null, null);
    }

    public BaseApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        this(status, code, message, null, null, details, null, extractRetryAfter(details));
    }

    public BaseApiException(HttpStatus status, String code, String message,
                            String messageKey, Object[] messageArgs,
                            Map<String, Object> details, List<String> errors, Long retryAfterSeconds) {
        super(message);
        this.status = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        this.code = code != null ? code : "INTERNAL_ERROR";
        this.messageKey = messageKey != null ? messageKey : ("error." + this.code);
        this.messageArgs = messageArgs;
        this.details = details == null ? Collections.emptyMap() : new LinkedHashMap<>(details);
        this.errors = errors;
        this.retryAfterSeconds = retryAfterSeconds != null ? retryAfterSeconds : extractRetryAfter(details);
    }

    private static Long extractRetryAfter(Map<String, Object> details) {
        if (details != null && details.get("retryAfterSeconds") instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
