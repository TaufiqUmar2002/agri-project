package com.umar.claims.exception;

import com.umar.exception.BaseApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class ClaimsApiException extends BaseApiException {
    public ClaimsApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public ClaimsApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }
}
