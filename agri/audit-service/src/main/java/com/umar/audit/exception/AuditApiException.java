package com.umar.audit.exception;

import com.umar.exception.BaseApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class AuditApiException extends BaseApiException {
    public AuditApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public AuditApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }
}
