package com.umar.billing.exception;

import com.umar.exception.ExceptionHook;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class BillingExceptionHook implements ExceptionHook {

    @Override
    public boolean supports(Throwable ex) {
        return ex instanceof BillingApiException b && b.isEmptyBody();
    }

    @Override
    public ResponseEntity<Map<String, Object>> handle(Throwable ex, MessageSource messageSource, Locale locale) {
        BillingApiException b = (BillingApiException) ex;
        return ResponseEntity.status(b.getStatus()).build();
    }
}
