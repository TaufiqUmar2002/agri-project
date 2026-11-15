package com.umar.audit;

public class AuditException extends RuntimeException {
    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }
}
