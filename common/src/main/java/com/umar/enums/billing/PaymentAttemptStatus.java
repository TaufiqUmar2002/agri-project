package com.umar.enums.billing;

public enum PaymentAttemptStatus {
    INITIATED,
    PENDING_PROVIDER,
    CHECKOUT_REQUIRED,
    REFERENCE_ISSUED,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    CANCELLED
}
