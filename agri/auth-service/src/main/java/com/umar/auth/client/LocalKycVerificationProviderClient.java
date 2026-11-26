package com.umar.auth.client;

import lombok.Setter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class LocalKycVerificationProviderClient implements KycVerificationProviderClient {

    @Setter
    private volatile boolean providerAvailable = true;

    @Setter
    private volatile BigDecimal defaultScore = new BigDecimal("0.95");

    @Override
    public VerificationOutcome verify(String kycId, String idType, String idNumberToken, String fullNameOnId, String dob) {
        if (!providerAvailable) {
            return new VerificationOutcome(false, null, false, null, List.of());
        }
        BigDecimal score = defaultScore;
        if (fullNameOnId != null && fullNameOnId.toUpperCase().contains("BORDERLINE")) {
            score = new BigDecimal("0.82");
        } else if (fullNameOnId != null && fullNameOnId.toUpperCase().contains("MISMATCH")) {
            score = new BigDecimal("0.55");
            return new VerificationOutcome(true, score, false, "prov-" + UUID.randomUUID(), List.of("SELFIE_MISMATCH"));
        }
        return new VerificationOutcome(true, score, true, "prov-" + UUID.randomUUID(), List.of());
    }
}
