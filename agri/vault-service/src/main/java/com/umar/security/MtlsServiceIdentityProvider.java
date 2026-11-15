package com.umar.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MtlsServiceIdentityProvider implements ServiceIdentityProvider{

    private static final String CERT_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";


    @Override
    public ServiceIdentity getCurrentIdentity() {
        X509Certificate certificate = getX509Certificate();
        try {
            certificate.checkValidity();
            Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
            if (sans == null) {
                throw new IllegalStateException("Certificate SAN is missing");
            }
            for (List<?> san : sans) {
                Integer type = (Integer) san.get(0);
                // SAN type 6 = URI
                if (type == 6 && san.size() > 1) {
                    String spiffeId = san.get(1).toString();
                    String serviceName = extractServiceName(spiffeId);
                    return new ServiceIdentity(serviceName, spiffeId);
                }
            }
            throw new IllegalStateException("SPIFFE URI SAN not found in certificate");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to validate service certificate identity", exception);
        }
    }

    private static X509Certificate getX509Certificate() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("No active HTTP request");
        }
        HttpServletRequest request = attributes.getRequest();
        X509Certificate[] certificates = (X509Certificate[]) request.getAttribute(CERT_ATTRIBUTE);
        if (certificates == null || certificates.length == 0) {
            throw new IllegalStateException("Authenticated mTLS certificate not found");
        }
        X509Certificate certificate = certificates[0];
        return certificate;
    }


    private String extractServiceName(String spiffeId) {
        // spiffe://example.org/ns/production/sa/user-service
        if (!spiffeId.startsWith("spiffe://")) {
            throw new IllegalStateException("Invalid SPIFFE identity");
        }
        String[] parts = spiffeId.split("/");
        if (parts.length < 6 || !"sa".equals(parts[parts.length - 2])) {
            throw new IllegalStateException("Unexpected SPIFFE identity format");
        }
        return parts[parts.length - 1].toUpperCase();
    }
}
