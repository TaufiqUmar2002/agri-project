package com.umar.farmer.exception;

import com.umar.exception.BaseApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class FarmerApiException extends BaseApiException {

    public FarmerApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public FarmerApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }
}
