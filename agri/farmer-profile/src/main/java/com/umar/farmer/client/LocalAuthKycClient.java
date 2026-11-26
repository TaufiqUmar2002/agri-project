package com.umar.farmer.client;

import com.umar.enums.auth.DelegationScope;
import com.umar.enums.auth.KycTier;
import com.umar.farmer.exception.FarmerApiException;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalAuthKycClient implements AuthKycClient {

    @Setter
    private volatile boolean unavailable = false;

    private final Map<UUID, IdentitySnapshot> identities = new ConcurrentHashMap<>();
    private final Map<UUID, DelegationRecord> delegations = new ConcurrentHashMap<>();
    private final Map<String, StepUpRecord> stepUpTokens = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.umar.integration.IntegrationPeerClient integrationPeerClient;

    @Override
    public IdentitySnapshot getIdentityContext(UUID userId) {
        if (unavailable) {
            throw new FarmerApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_SERVICE_UNAVAILABLE",
                    "Please try again in a moment");
        }
        if (integrationPeerClient != null) {
            integrationPeerClient.getAuthUser(userId.toString());
        }
        return identities.getOrDefault(userId,
                new IdentitySnapshot("ACTIVE", KycTier.TIER_1, Set.of("FARMER"), true));
    }

    @Override
    public UUID validateDelegation(UUID delegationId, UUID agentId, DelegationScope requiredScope) {
        if (unavailable) {
            throw new FarmerApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_SERVICE_UNAVAILABLE",
                    "Please try again in a moment");
        }
        DelegationRecord rec = delegations.get(delegationId);
        if (rec == null || !rec.agentId().equals(agentId) || !rec.scopes().contains(requiredScope)) {
            throw new FarmerApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Not authorised to act for this farmer");
        }
        return rec.farmerUserId();
    }

    @Override
    public void verifyStepUpToken(String stepUpToken, UUID userId, String actionClass) {
        if (stepUpToken == null || stepUpToken.isBlank()) {
            throw new FarmerApiException(HttpStatus.UNAUTHORIZED, "STEP_UP_REQUIRED",
                    "Please confirm with a new code", Map.of("stepUp", true));
        }
        StepUpRecord rec = stepUpTokens.get(stepUpToken);
        if (rec != null) {
            if (!rec.userId().equals(userId) || !rec.actionClass().equals(actionClass)) {
                throw new FarmerApiException(HttpStatus.UNAUTHORIZED, "STEP_UP_REQUIRED",
                        "Please confirm with a new code", Map.of("stepUp", true));
            }
            stepUpTokens.remove(stepUpToken);
        } else if (!stepUpToken.startsWith("su_")) {
            throw new FarmerApiException(HttpStatus.UNAUTHORIZED, "STEP_UP_REQUIRED",
                    "Please confirm with a new code", Map.of("stepUp", true));
        }
    }

    public void registerIdentity(UUID userId, IdentitySnapshot snapshot) {
        identities.put(userId, snapshot);
    }

    public void registerDelegation(UUID delegationId, UUID agentId, UUID farmerUserId, Set<DelegationScope> scopes) {
        delegations.put(delegationId, new DelegationRecord(agentId, farmerUserId, scopes));
    }

    public void registerStepUp(String token, UUID userId, String actionClass) {
        stepUpTokens.put(token, new StepUpRecord(userId, actionClass));
    }

    private record DelegationRecord(UUID agentId, UUID farmerUserId, Set<DelegationScope> scopes) {}
    private record StepUpRecord(UUID userId, String actionClass) {}
}
