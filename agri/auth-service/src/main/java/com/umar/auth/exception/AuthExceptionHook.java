package com.umar.auth.exception;

import com.umar.exception.BaseApiException;
import com.umar.exception.ExceptionHook;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class AuthExceptionHook implements ExceptionHook {

    @Override
    public boolean supports(Throwable ex) {
        return ex instanceof MissingRequestHeaderException m && "X-Step-Up-Token".equalsIgnoreCase(m.getHeaderName());
    }

    @Override
    public ResponseEntity<Map<String, Object>> handle(Throwable ex, MessageSource messageSource, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "STEP_UP_REQUIRED");
        body.put("error", "Please confirm with a new code");
        body.put("message", "Please confirm with a new code");
        body.put("stepUp", true);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"agrishield\"")
                .body(body);
    }

    @Override
    public void customizeResponse(BaseApiException ex, Map<String, Object> body, HttpHeaders headers) {
        if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"agrishield\"");
        }
    }
}
