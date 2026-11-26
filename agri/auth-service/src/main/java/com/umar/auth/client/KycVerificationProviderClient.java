package com.umar.auth.client;

import java.math.BigDecimal;
import java.util.List;

public interface KycVerificationProviderClient {

    VerificationOutcome verify(String kycId, String idType, String idNumberToken, String fullNameOnId, String dob);

    record VerificationOutcome(
            boolean providerAvailable,
            BigDecimal score,
            boolean allFieldsMatch,
            String providerRef,
            List<String> reasonCodes
    ) {}
}
