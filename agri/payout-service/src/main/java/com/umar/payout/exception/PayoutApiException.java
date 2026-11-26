package com.umar.payout.exception;

import com.umar.exception.BaseApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class PayoutApiException extends BaseApiException {
    public PayoutApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public PayoutApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }
}
