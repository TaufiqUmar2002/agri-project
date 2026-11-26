package com.umar.auth.client;

import java.util.Map;

public interface HookSdkClient {
    HookResult invokePreRegister(String phoneHash, String channel, String source);
    Map<String, Object> enrichTokenClaims(String userId, String userType);
    void postLogin(String userId, String channel);
    void postKycDecision(String kycId, String userId, String status);
    void postConsentRecord(String consentId, String userId, String purpose, String changeType);

    record HookResult(boolean allowed, String reason) {}
}
