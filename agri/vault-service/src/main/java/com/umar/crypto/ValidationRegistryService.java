package com.umar.crypto;


import org.springframework.stereotype.Service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Section 2.5 & 4.1 - Seed Registry Data Class Validation Engine.
 * Implements validation rules, normalization constraints, and legacy format (FPE) access controls.
 */
@Service
public class ValidationRegistryService {

    // Pre-compiled threat and canonical validation patterns to hit low-latency targets (<20ms)
    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final Pattern ALPHANUMERIC_ID_PATTERN = Pattern.compile("^[A-Z0-9]{5,30}$");
    private static final Pattern WALLET_NUMBER_PATTERN = Pattern.compile("^\\d{9,15}$");

    // Dynamic registry map tracking which data classes are legally permitted to leverage legacy FPE formats
    private final Set<String> fpeAllowedClasses = Set.of("PHONE", "WALLET_NUMBER");

    /**
     * Step 3: Check item formatting rules without copying characters into immutable strings [4.1]
     */
    public boolean isValidForClass(char[] rawValue, String dataClass) {
        if (rawValue == null || rawValue.length == 0 || dataClass == null) {
            return false;
        }

        // Limit maximum character bounds upfront per requirement spec (up to 512 characters)
        if (rawValue.length > 512) {
            return false;
        }

        String dataClassUpper = dataClass.toUpperCase();

        // Convert transiently inside validation step frame to avoid dirtying GC heap
        CharSequence inputSeq = java.nio.CharBuffer.wrap(rawValue);

        return switch (dataClassUpper) {
            case "PHONE", "PAYER_PHONE" -> E164_PHONE_PATTERN.matcher(inputSeq).matches();
            case "ID_NUMBER" -> ALPHANUMERIC_ID_PATTERN.matcher(inputSeq).matches();
            case "WALLET_NUMBER" -> WALLET_NUMBER_PATTERN.matcher(inputSeq).matches();
            case "LEGAL_NAME" -> validateLegalName(rawValue);
            default -> true; // Fallback allowance for unconstrained unstructured types (e.g. documents)
        };
    }

    /**
     * Scenario 4: Validates if Format-Preserving Encryption tokens are permitted for the data class.
     */
    public boolean isFpePermitted(String dataClass) {
        if (dataClass == null) return false;
        return fpeAllowedClasses.contains(dataClass.toUpperCase());
    }

    /**
     * Low-level parsing array loop to check rules without executing String instantiations.
     */
    private boolean validateLegalName(char[] nameArray) {
        if (nameArray.length < 2) return false;
        for (char c : nameArray) {
            // Rejects letters/numbers mismatch anomalies at the boundary door
            if (!Character.isLetter(c) && c != ' ' && c != '-' && c != '\'') {
                return false; // Scenario 6: Rejects data anomalies cleanly
            }
        }
        return true;
    }
}
