package com.umar.iot.exception;

import com.umar.exception.BaseApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class IotApiException extends BaseApiException {
    public IotApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public IotApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }
}
