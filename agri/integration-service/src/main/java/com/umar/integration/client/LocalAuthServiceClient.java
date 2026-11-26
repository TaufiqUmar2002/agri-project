package com.umar.integration.client;

import com.umar.integration.util.CryptoUtils;
import org.springframework.stereotype.Component;

@Component
public class LocalAuthServiceClient implements AuthServiceClient {

    @Override
    public IssuedPartnerCredentials issuePartnerCredentials(String partnerId, String tenantId, boolean mtlsRequired) {
        String clientId = "oauth_" + partnerId + "_" + CryptoUtils.randomHex(4);
        String mtlsId = mtlsRequired ? "mtls_" + partnerId + "_" + CryptoUtils.randomHex(4) : null;
        return new IssuedPartnerCredentials(clientId, mtlsId);
    }
}
