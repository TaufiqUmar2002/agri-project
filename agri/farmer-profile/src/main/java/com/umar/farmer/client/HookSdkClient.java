package com.umar.farmer.client;

import java.util.Map;

public interface HookSdkClient {
    Map<String, Object> enrichProfile(String farmerId);
    WalletHookDecision preWalletChange(String farmerId, String provider);
    GroupHookDecision preCreateGroup(String name, String registrationNumber);
    void postCreate(String farmerId);
    void postUpdate(String farmerId);

    enum WalletHookDecision { ALLOW, REQUIRE_REVIEW, REJECT }
    enum GroupHookDecision { ALLOW, HOLD_FOR_REVIEW }
}
