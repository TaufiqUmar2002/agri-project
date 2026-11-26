package com.umar.auth.client;

public interface NotificationClient {
    String sendOtp(String phoneToken, String otpCode, String language, String channel);
    void sendSecurityAlert(String phoneToken, String templateCode, String language);
}
