package com.umar.trigger.exception;

import com.umar.exception.BaseApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class TriggerApiException extends BaseApiException {
    public TriggerApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public TriggerApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }
}
