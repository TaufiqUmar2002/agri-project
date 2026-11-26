package com.umar.crypto;

import com.umar.entity.dataProtection.DataClass;
import com.umar.repository.dataProtection.DataClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.CharBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Section 2.5 & 4.1 - Seed & Dynamic Data Class Validation Engine.
 * Implements validation rules, normalization constraints, and FPE access controls.
 */
@Service
public class ValidationRegistryService {

    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final Pattern ALPHANUMERIC_ID_PATTERN = Pattern.compile("^[A-Za-z0-9\\-]{5,30}$");
    private static final Pattern WALLET_NUMBER_PATTERN = Pattern.compile("^(\\+)?\\d{7,15}$");
    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z0-9\\-\\s]{6,34}$");

    private final Set<String> fpeAllowedClasses = Set.of("PHONE", "WALLET_NUMBER");
    private final DataClassRepository dataClassRepository;

    public ValidationRegistryService() {
        this.dataClassRepository = null;
    }

    @Autowired
    public ValidationRegistryService(DataClassRepository dataClassRepository) {
        this.dataClassRepository = dataClassRepository;
    }

    /**
     * Step 3: Check item formatting rules without copying characters into immutable strings [4.1].
     */
    public boolean isValidForClass(char[] rawValue, String dataClass) {
        if (rawValue == null || rawValue.length == 0 || dataClass == null) {
            return false;
        }
        if (rawValue.length > 512) {
            return false;
        }

        String dataClassUpper = dataClass.trim().toUpperCase();
        CharSequence inputSeq = CharBuffer.wrap(rawValue);

        if (dataClassRepository != null) {
            Optional<DataClass> dc = dataClassRepository.findByNameAndStatus(dataClassUpper, "ACTIVE");
            if (dc.isPresent() && dc.get().getValidation() != null
                    && "REGEX".equalsIgnoreCase(dc.get().getValidation().getType())
                    && dc.get().getValidation().getFormat() != null
                    && !dc.get().getValidation().getFormat().isBlank()) {
                Pattern custom = Pattern.compile(dc.get().getValidation().getFormat());
                if (custom.matcher(inputSeq).matches()) {
                    return true;
                }
                if (!isKnownSeedClass(dataClassUpper)) {
                    return false;
                }
            }
        }

        return switch (dataClassUpper) {
            case "PHONE", "PAYER_PHONE" -> E164_PHONE_PATTERN.matcher(inputSeq).matches();
            case "ID_NUMBER" -> ALPHANUMERIC_ID_PATTERN.matcher(inputSeq).matches();
            case "WALLET_NUMBER" -> WALLET_NUMBER_PATTERN.matcher(inputSeq).matches();
            case "SPONSOR_BANK_ACCOUNT" -> BANK_ACCOUNT_PATTERN.matcher(inputSeq).matches();
            case "LEGAL_NAME" -> validateLegalName(rawValue);
            default -> true;
        };
    }

    public String canonicalize(String rawValue, String dataClass) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (dataClass == null) {
            return trimmed;
        }
        return switch (dataClass.trim().toUpperCase()) {
            case "PHONE", "PAYER_PHONE" -> trimmed.replaceAll("[\\s()-]", "");
            case "ID_NUMBER", "SPONSOR_BANK_ACCOUNT" -> trimmed.toUpperCase();
            default -> trimmed;
        };
    }

    /**
     * Scenario 4: Validates if Format-Preserving Encryption tokens are permitted for the data class.
     */
    public boolean isFpePermitted(String dataClass) {
        if (dataClass == null) return false;
        String upper = dataClass.trim().toUpperCase();
        if (dataClassRepository != null) {
            Optional<DataClass> dc = dataClassRepository.findByNameAndStatus(upper, "ACTIVE");
            if (dc.isPresent() && dc.get().getFpe() != null) {
                if ("true".equalsIgnoreCase(dc.get().getFpe())) {
                    return true;
                }
                if ("false".equalsIgnoreCase(dc.get().getFpe()) && !fpeAllowedClasses.contains(upper)) {
                    return false;
                }
            }
        }
        return fpeAllowedClasses.contains(upper);
    }

    private boolean isKnownSeedClass(String upper) {
        return Set.of("PHONE", "PAYER_PHONE", "ID_NUMBER", "WALLET_NUMBER", "LEGAL_NAME", "SPONSOR_BANK_ACCOUNT")
                .contains(upper);
    }

    private boolean validateLegalName(char[] nameArray) {
        if (nameArray.length < 2) return false;
        for (char c : nameArray) {
            if (!Character.isLetter(c) && c != ' ' && c != '-' && c != '\'' && c != '.') {
                return false;
            }
        }
        return true;
    }
}
