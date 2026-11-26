package com.umar.farmer.client;

import com.umar.enums.auth.DelegationScope;
import com.umar.enums.auth.KycTier;

import java.util.Set;
import java.util.UUID;

public interface AuthKycClient {
    IdentitySnapshot getIdentityContext(UUID userId);
    UUID validateDelegation(UUID delegationId, UUID agentId, DelegationScope requiredScope);
    void verifyStepUpToken(String stepUpToken, UUID userId, String actionClass);

    record IdentitySnapshot(String accountStatus, KycTier kycTier, Set<String> roles, boolean dataCollectionConsent) {}
}
