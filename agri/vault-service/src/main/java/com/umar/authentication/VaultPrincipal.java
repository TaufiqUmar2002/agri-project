package com.umar.authentication;

public record VaultPrincipal(String serviceIdentity, String certificateSubject, String certificateSerialNumber) {
}
