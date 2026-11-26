package com.umar.integration.client;

public interface AuthServiceClient {

    IssuedPartnerCredentials issuePartnerCredentials(String partnerId, String tenantId, boolean mtlsRequired);

    record IssuedPartnerCredentials(String oauthClientId, String mtlsProfileId) {}
}
