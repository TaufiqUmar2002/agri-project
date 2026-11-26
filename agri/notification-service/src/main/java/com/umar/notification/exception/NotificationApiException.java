package com.umar.notification.exception;

import com.umar.exception.BaseApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class NotificationApiException extends BaseApiException {
    public NotificationApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public NotificationApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }
}
