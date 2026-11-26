package com.umar.plot.client;

import com.umar.enums.auth.DelegationScope;
import java.util.UUID;

public interface AuthKycClient {
    boolean validateDelegation(UUID delegationId, UUID agentId, UUID farmerId, DelegationScope scope);
}
