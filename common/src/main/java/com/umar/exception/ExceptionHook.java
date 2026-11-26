package com.umar.exception;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Locale;
import java.util.Map;

/**
 * Extension hook interface that individual microservices can implement as Spring beans
 * to customize error resolution, handle service-specific legacy exceptions, or inject headers.
 */
public interface ExceptionHook {

    default boolean supports(Throwable ex) {
        return false;
    }

    default ResponseEntity<Map<String, Object>> handle(Throwable ex, MessageSource messageSource, Locale locale) {
        return null;
    }

    default void customizeResponse(BaseApiException ex, Map<String, Object> body, HttpHeaders headers) {
    }
}
