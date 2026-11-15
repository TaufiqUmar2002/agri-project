package com.umar.service;

import org.springframework.stereotype.Component;

@Component
public class DataValidator {

    public String normalize(String dataClass, String value) {

        return switch (dataClass) {
            case "PHONE" -> normalizePhone(value);
            case "LEGAL_NAME" -> value.trim();
            case "WALLET_NUMBER" -> value.trim();
            default -> value.trim();
        };
    }

    private String normalizePhone(String value) {

        String normalized = value.replaceAll("[\\s()-]", "");

        if (!normalized.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new IllegalArgumentException(
                    "Invalid phone number"
            );
        }

        return normalized;
    }
}
