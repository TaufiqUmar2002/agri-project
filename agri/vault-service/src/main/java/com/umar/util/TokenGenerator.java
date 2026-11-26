package com.umar.util;

import com.umar.converter.MaskingRule;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class TokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a completely opaque token string matching the exact format:
     * Values: vt_<class>_<128-bit random hex string>
     * Documents: vt_doc_<class>_<128-bit random hex string>
     */
    public String mintTokenString(String dataClass, boolean isDocument) {
        byte[] randomBytes = new byte[16]; // 128 bits of true entropy
        secureRandom.nextBytes(randomBytes);
        String randomHexId = HexFormat.of().formatHex(randomBytes);

        String prefix = isDocument ? "vt_doc_" : "vt_";
        return prefix + dataClass.toLowerCase() + "_" + randomHexId;
    }

    /**
     * Creates a safe display hint extraction format (Masked Form) from the raw value
     * according to the data class masking rule (section 2.3).
     */
    public String createValueMaskedHint(String canonicalValue, MaskingRule rule) {
        if (canonicalValue == null || canonicalValue.isEmpty()) {
            return "***";
        }
        int showLast = (rule != null && rule.getCharacters() > 0) ? rule.getCharacters() : 3;
        if (rule != null && "MASK_ALL".equalsIgnoreCase(rule.getStrategy())) {
            return "*".repeat(Math.min(canonicalValue.length(), 8));
        }
        int len = canonicalValue.length();
        if (len <= showLast) {
            return "*".repeat(len);
        }
        int visiblePrefix = Math.min(canonicalValue.startsWith("+") ? 5 : 0, len - showLast - 1);
        if (visiblePrefix > 0) {
            return canonicalValue.substring(0, visiblePrefix)
                    + "*".repeat(len - visiblePrefix - showLast)
                    + canonicalValue.substring(len - showLast);
        }
        return "*".repeat(len - showLast) + canonicalValue.substring(len - showLast);
    }

    /**
     * Creates a safe display hint extraction format (Masked Form)
     * without calling detokenization [2.3].
     */
    public String createMaskedHint(String tokenString) {
        if (tokenString == null || tokenString.length() < 3) {
            return "***";
        }
        // Grab the last 3 characters of the random string component as a hint
        return "***" + tokenString.substring(tokenString.length() - 3);
    }

    public String generate(String dataClass) {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        String randomId = HexFormat.of().formatHex(randomBytes);
        String prefix = dataClass.toLowerCase().replace("_", "");
        return "vt_" + prefix + "_" + randomId;
    }

}
