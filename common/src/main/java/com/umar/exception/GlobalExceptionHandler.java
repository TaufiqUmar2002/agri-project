package com.umar.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Centralized REST exception handler shared across all AgreeShield microservices.
 * Resolves localized messages via MessageSource and invokes per-service ExceptionHook beans.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;
    private final List<ExceptionHook> exceptionHooks;
    private final org.springframework.beans.factory.ObjectProvider<com.umar.logging.CentralLoggingService> centralLoggingProvider;

    @ExceptionHandler(BaseApiException.class)
    public ResponseEntity<Map<String, Object>> handleBaseApi(BaseApiException ex) {
        if (centralLoggingProvider != null && centralLoggingProvider.getIfAvailable() != null) {
            centralLoggingProvider.getObject().exception("GlobalExceptionHandler", ex.getCode(), ex);
        }
        Locale locale = LocaleContextHolder.getLocale();
        for (ExceptionHook hook : exceptionHooks) {
            if (hook.supports(ex)) {
                ResponseEntity<Map<String, Object>> custom = hook.handle(ex, messageSource, locale);
                if (custom != null) return custom;
            }
        }
        String resolvedMessage = resolveMessage(ex.getMessageKey(), ex.getMessageArgs(), ex.getMessage(), locale);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getCode());
        if (ex.getErrors() != null && !ex.getErrors().isEmpty()) {
            body.put("errors", ex.getErrors());
        }
        body.put("error", resolvedMessage);
        body.put("message", resolvedMessage);
        body.put("timestamp", Instant.now().toString());
        if (ex.getDetails() != null && !ex.getDetails().isEmpty()) {
            body.putAll(ex.getDetails());
        }

        HttpHeaders headers = new HttpHeaders();
        if (ex.getRetryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        for (ExceptionHook hook : exceptionHooks) {
            hook.customizeResponse(ex, body, headers);
        }
        return ResponseEntity.status(ex.getStatus()).headers(headers).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        return buildValidation(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return buildValidation(errors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        for (ExceptionHook hook : exceptionHooks) {
            if (hook.supports(ex)) {
                ResponseEntity<Map<String, Object>> custom = hook.handle(ex, messageSource, locale);
                if (custom != null) return custom;
            }
        }
        if ("If-Match".equalsIgnoreCase(ex.getHeaderName())) {
            return buildSimple(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "If-Match header is required", locale);
        }
        return buildValidation(List.of("Missing required header: " + ex.getHeaderName()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return buildValidation(List.of("Missing required parameter: " + ex.getParameterName()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return buildSimple(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File too large", LocaleContextHolder.getLocale());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request payload: {}", ex.getMessage());
        return buildValidation(List.of("Malformed request payload"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        for (ExceptionHook hook : exceptionHooks) {
            if (hook.supports(ex)) {
                ResponseEntity<Map<String, Object>> custom = hook.handle(ex, messageSource, locale);
                if (custom != null) return custom;
            }
        }
        String msg = ex.getMessage() != null ? ex.getMessage() : "Invalid request";
        if (msg.toLowerCase().contains("not found")) {
            return buildSimple(HttpStatus.NOT_FOUND, "NOT_FOUND", msg, locale);
        }
        return buildSimple(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg, locale);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        for (ExceptionHook hook : exceptionHooks) {
            if (hook.supports(ex)) {
                ResponseEntity<Map<String, Object>> custom = hook.handle(ex, messageSource, locale);
                if (custom != null) return custom;
            }
        }
        String msg = ex.getMessage() != null ? ex.getMessage() : "Conflict";
        return buildSimple(HttpStatus.CONFLICT, "CONFLICT", msg, locale);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        Locale locale = LocaleContextHolder.getLocale();
        for (ExceptionHook hook : exceptionHooks) {
            if (hook.supports(ex)) {
                ResponseEntity<Map<String, Object>> custom = hook.handle(ex, messageSource, locale);
                if (custom != null) return custom;
            }
        }
        log.error("Unhandled microservice exception", ex);
        return buildSimple(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected internal error occurred", locale);
    }

    private ResponseEntity<Map<String, Object>> buildValidation(List<String> errors) {
        Locale locale = LocaleContextHolder.getLocale();
        String msg = errors.isEmpty()
                ? resolveMessage("error.VALIDATION_ERROR", null, "Validation failed", locale)
                : errors.getFirst();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "VALIDATION_ERROR");
        body.put("error", msg);
        body.put("message", msg);
        body.put("errors", errors);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> buildSimple(HttpStatus status, String code, String fallbackMsg, Locale locale) {
        String resolved = resolveMessage("error." + code, null, fallbackMsg, locale);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("error", resolved);
        body.put("message", resolved);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    private String resolveMessage(String key, Object[] args, String fallback, Locale locale) {
        if (fallback != null && !fallback.isBlank()) {
            // Preserve explicit domain message if no custom override template differs, OR allow localized format via messageSource
            String lookedUp = messageSource.getMessage(key, args, "__MISSING__", locale);
            if (!"__MISSING__".equals(lookedUp) && (fallback == null || fallback.isBlank())) {
                return lookedUp;
            }
            return fallback;
        }
        return messageSource.getMessage(key, args, key, locale);
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid");
    }
}
