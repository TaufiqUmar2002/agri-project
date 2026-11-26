package com.umar.farmer.client;

import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LocalHookSdkClient implements HookSdkClient {

    @Setter
    private volatile WalletHookDecision walletDecision = WalletHookDecision.ALLOW;

    @Override
    public Map<String, Object> enrichProfile(String farmerId) {
        return Map.of();
    }

    @Override
    public WalletHookDecision preWalletChange(String farmerId, String provider) {
        return walletDecision;
    }

    @Override
    public GroupHookDecision preCreateGroup(String name, String registrationNumber) {
        return GroupHookDecision.ALLOW;
    }

    @Override
    public void postCreate(String farmerId) {}

    @Override
    public void postUpdate(String farmerId) {}
}
