package com.umar.plot.client;

import com.umar.enums.auth.DelegationScope;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalAuthKycClient implements AuthKycClient {

    @Setter private volatile boolean unavailable = false;
    private final Map<UUID, Rec> delegations = new ConcurrentHashMap<>();

    @Override
    public boolean validateDelegation(UUID delegationId, UUID agentId, UUID farmerId, DelegationScope scope) {
        if (unavailable) return false;
        Rec r = delegations.get(delegationId);
        return r != null && r.agentId().equals(agentId) && r.farmerId().equals(farmerId) && r.scopes().contains(scope);
    }

    public void registerDelegation(UUID delegationId, UUID agentId, UUID farmerId, Set<DelegationScope> scopes) {
        delegations.put(delegationId, new Rec(agentId, farmerId, scopes));
    }

    private record Rec(UUID agentId, UUID farmerId, Set<DelegationScope> scopes) {}
}
