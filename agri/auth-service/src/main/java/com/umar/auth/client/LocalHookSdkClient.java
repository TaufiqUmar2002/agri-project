package com.umar.auth.client;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalHookSdkClient implements HookSdkClient {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.umar.integration.IntegrationPeerClient integrationPeerClient;

    @Getter
    private final Set<String> blockedPhoneHashes = ConcurrentHashMap.newKeySet();

    @Override
    public HookResult invokePreRegister(String phoneHash, String channel, String source) {
        if (blockedPhoneHashes.contains(phoneHash)) {
            return new HookResult(false, "BLOCKLISTED_NUMBER");
        }
        if (integrationPeerClient != null) {
            integrationPeerClient.invokeExtension("auth.pre-register", Map.of("phoneHash", phoneHash, "channel", String.valueOf(channel)));
        }
        return new HookResult(true, null);
    }

    @Override
    public Map<String, Object> enrichTokenClaims(String userId, String userType) {
        return Map.of();
    }

    @Override
    public void postLogin(String userId, String channel) {}

    @Override
    public void postKycDecision(String kycId, String userId, String status) {}

    @Override
    public void postConsentRecord(String consentId, String userId, String purpose, String changeType) {}
}
