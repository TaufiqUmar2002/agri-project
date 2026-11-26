package com.umar.authentication;

import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CertificateIdentityResolver {

    private static final String SPIFFE_PREFIX = "spiffe://agrishield/";
    private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,\\s]+)");

    public VaultPrincipal resolve(X509Certificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("Client certificate missing");
        }
        try {
            certificate.checkValidity();
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names != null) {
                String identity = names.stream()
                        .filter(entry -> entry.size() >= 2 && Integer.valueOf(6).equals(entry.get(0)))
                        .map(entry -> String.valueOf(entry.get(1)))
                        .filter(uri -> uri.startsWith(SPIFFE_PREFIX) || uri.startsWith("spiffe://"))
                        .findFirst()
                        .orElse(null);
                if (identity != null) {
                    return new VaultPrincipal(
                            identity,
                            certificate.getSubjectX500Principal().getName(),
                            certificate.getSerialNumber().toString());
                }
            }
            String dn = certificate.getSubjectX500Principal().getName();
            Matcher matcher = CN_PATTERN.matcher(dn);
            if (matcher.find()) {
                return new VaultPrincipal(
                        matcher.group(1),
                        dn,
                        certificate.getSerialNumber().toString());
            }
            throw new IllegalArgumentException("Valid SPIFFE identity not found");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid client certificate identity", ex);
        }
    }

    public WorkloadIdentity toWorkloadIdentity(X509Certificate certificate) {
        VaultPrincipal principal = resolve(certificate);
        String spiffeOrCn = principal.serviceIdentity();
        if (spiffeOrCn != null && spiffeOrCn.startsWith("spiffe://")) {
            String path = spiffeOrCn.substring("spiffe://".length());
            String[] parts = path.split("/");
            String env = "prod";
            for (int i = 0; i < parts.length - 1; i++) {
                if ("ns".equals(parts[i])) {
                    env = parts[i + 1];
                    break;
                }
            }
            String serviceName = parts[parts.length - 1].toUpperCase();
            return new WorkloadIdentity(serviceName, env, Set.of("SERVICE"));
        }
        return new WorkloadIdentity(spiffeOrCn != null ? spiffeOrCn.toUpperCase() : "UNKNOWN", "mtls", Set.of("SERVICE"));
    }
}
