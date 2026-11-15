package com.umar.authentication;


import org.springframework.stereotype.Component;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CertificateIdentityResolver {


    private static final String SPIFFE_PREFIX =
            "spiffe://agrishield/";

    public VaultPrincipal resolve(X509Certificate certificate) {

        if (certificate == null) {
            throw new IllegalArgumentException(
                    "Client certificate missing"
            );
        }

        try {
            Collection<List<?>> names =
                    certificate.getSubjectAlternativeNames();

            if (names == null) {
                throw new IllegalArgumentException(
                        "Certificate SAN missing"
                );
            }

            String identity = names.stream()
                    .filter(entry -> Integer.valueOf(6)
                            .equals(entry.get(0)))
                    .map(entry -> String.valueOf(entry.get(1)))
                    .filter(uri -> uri.startsWith(SPIFFE_PREFIX))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Valid SPIFFE identity not found"
                            ));

            return new VaultPrincipal(
                    identity,
                    certificate.getSubjectX500Principal()
                            .getName(),
                    certificate.getSerialNumber().toString()
            );

        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid client certificate identity", ex
            );
        }
    }



//    private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,\\s]+)");
//
//    /**
//     * Step 2 & 3: Resolves X509Certificate fields to extract the exact Workload Identity.
//     */
//    public WorkloadIdentity resolve(X509Certificate certificate) {
//        if (certificate == null) {
//            throw new IllegalArgumentException("No client certificate provided in secure context.");
//        }
//
//        // Try extracting SPIFFE IDs or URIs from Subject Alternative Names (SAN) first
//        try {
//            Collection<List<?>> sanEntries = certificate.getSubjectAlternativeNames();
//            if (sanEntries != null) {
//                for (List<?> entry : sanEntries) {
//                    // Type 6 is UniformResourceIdentifier (URI) -> Ideal for SPIFFE IDs
//                    if (entry.size() >= 2 && entry.get(0).equals(6)) {
//                        String uri = (String) entry.get(1);
//                        if (uri.startsWith("spiffe://")) {
//                            return parseSpiffeId(uri);
//                        }
//                    }
//                }
//            }
//        } catch (Exception ignored) {
//            // Fall back to legacy Common Name pattern matching if SAN parsing fails
//        }
//
//        // Fallback: Parse Common Name (CN) from Subject Principal
//        String dn = certificate.getSubjectX500Principal().getName();
//        Matcher matcher = CN_PATTERN.matcher(dn);
//        if (matcher.find()) {
//            String commonName = matcher.group(1);
//            // Splitting names if format looks like "service-name.environment"
//            if (commonName.contains(".")) {
//                String[] parts = commonName.split("\\.", 2);
//                return new WorkloadIdentity(parts[0], parts[1],null);
//            }
//            return new WorkloadIdentity(commonName, "unknown",null);
//        }
//
//        throw new IllegalStateException("Failed to resolve machine identity from client certificate.");
//    }
//
//    private WorkloadIdentity parseSpiffeId(String spiffeUri) {
//        // Example: spiffe://cluster.local/ns/production/sa/business-service
//        String path = spiffeUri.replace("spiffe://", "");
//        String[] pathParts = path.split("/");
//
//        String environment = "unknown";
//        String serviceName = pathParts[pathParts.length - 1];
//
//        // Infer environment from namespace segment if available
//        for (int i = 0; i < pathParts.length; i++) {
//            if ("ns".equals(pathParts[i]) && i + 1 < pathParts.length) {
//                environment = pathParts[i + 1];
//                break;
//            }
//        }
//        return new WorkloadIdentity(serviceName, environment,null);
//    }
}
