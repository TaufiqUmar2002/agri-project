package com.umar.integration.error;

import com.umar.exception.ExceptionHook;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Component
public class HubExceptionHook implements ExceptionHook {

    @Override
    public boolean supports(Throwable ex) {
        return ex instanceof MissingRequestHeaderException m && "If-Match".equalsIgnoreCase(m.getHeaderName());
    }

    @Override
    public ResponseEntity<Map<String, Object>> handle(Throwable ex, MessageSource messageSource, Locale locale) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(Map.of(
                "code", "PRECONDITION_REQUIRED",
                "error", "If-Match header is required",
                "timestamp", Instant.now().toString()
        ));
    }
}
