package com.umar.billing.exception;

import com.umar.exception.BaseApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class BillingApiException extends BaseApiException {
    private final boolean emptyBody;

    public BillingApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of(), false);
    }

    public BillingApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        this(status, code, message, details, false);
    }

    public BillingApiException(HttpStatus status, String code, String message, Map<String, Object> details, boolean emptyBody) {
        super(status, code, message, details);
        this.emptyBody = emptyBody;
    }
}
