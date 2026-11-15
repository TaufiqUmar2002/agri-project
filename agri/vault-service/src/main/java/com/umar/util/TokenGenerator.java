package com.umar.util;

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
