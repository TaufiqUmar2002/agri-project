package com.umar.auth.exception;

import com.umar.exception.BaseApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class AuthApiException extends BaseApiException {

    public AuthApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public AuthApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }
}
