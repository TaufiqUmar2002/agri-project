package com.umar.integration.api.grpc;

import io.grpc.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

@Component
public class HubGrpcAuthInterceptor implements ServerInterceptor {

    public static final Context.Key<String> SERVICE_IDENTITY_CTX = Context.key("serviceIdentity");
    private static final Metadata.Key<String> SERVICE_HEADER =
            Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${hub.auth.require-mtls:false}")
    private boolean requireMtls;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String serviceName = resolveFromSsl(call);
        if (serviceName == null && !requireMtls) {
            String headerVal = headers.get(SERVICE_HEADER);
            if (headerVal != null && !headerVal.isBlank()) {
                serviceName = headerVal.trim();
            }
        }

        if (serviceName == null || serviceName.isBlank()) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing service token or mTLS identity"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        Context ctx = Context.current().withValue(SERVICE_IDENTITY_CTX, serviceName);
        return Contexts.interceptCall(ctx, call, headers, next);
    }

    private <ReqT, RespT> String resolveFromSsl(ServerCall<ReqT, RespT> call) {
        try {
            SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
            if (session != null) {
                Certificate[] certs = session.getPeerCertificates();
                if (certs != null && certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                    String dn = x509.getSubjectX500Principal().getName();
                    for (String part : dn.split(",")) {
                        if (part.trim().startsWith("CN=")) {
                            return part.trim().substring(3);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
