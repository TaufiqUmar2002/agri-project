package com.umar.api.grpc;

import com.umar.authentication.CertificateIdentityResolver;
import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Set;

/**
 * Extracts mTLS client certificate identity (or configured dev metadata header)
 * into {@link VaultSecurityContext} for gRPC calls (VLT-11).
 */
@Component
@RequiredArgsConstructor
public class GrpcMtlsAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> CALLER_META_KEY =
            Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER);

    private final CertificateIdentityResolver certificateIdentityResolver;

    @Value("${vault.auth.require-mtls:false}")
    private boolean requireMtls;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        WorkloadIdentity identity = resolveIdentity(call, headers);
        if (identity == null && requireMtls) {
            call.close(Status.UNAUTHENTICATED.withDescription("No valid service identity"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        if (identity == null) {
            identity = new WorkloadIdentity("TEMP-SERVICE", "local", Set.of("SERVICE"));
        }

        final WorkloadIdentity resolved = identity;
        VaultSecurityContext.setWorkloadIdentity(resolved);
        try {
            ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
                @Override
                public void onMessage(ReqT message) {
                    VaultSecurityContext.setWorkloadIdentity(resolved);
                    try {
                        super.onMessage(message);
                    } finally {
                        VaultSecurityContext.clear();
                    }
                }

                @Override
                public void onHalfClose() {
                    VaultSecurityContext.setWorkloadIdentity(resolved);
                    try {
                        super.onHalfClose();
                    } finally {
                        VaultSecurityContext.clear();
                    }
                }

                @Override
                public void onComplete() {
                    try {
                        super.onComplete();
                    } finally {
                        VaultSecurityContext.clear();
                    }
                }

                @Override
                public void onCancel() {
                    try {
                        super.onCancel();
                    } finally {
                        VaultSecurityContext.clear();
                    }
                }
            };
        } finally {
            VaultSecurityContext.clear();
        }
    }

    private <ReqT, RespT> WorkloadIdentity resolveIdentity(ServerCall<ReqT, RespT> call, Metadata headers) {
        try {
            SSLSession sslSession = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
            if (sslSession != null) {
                Certificate[] certs = sslSession.getPeerCertificates();
                if (certs != null && certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                    return certificateIdentityResolver.toWorkloadIdentity(x509);
                }
            }
        } catch (Exception ignored) {
        }

        if (!requireMtls) {
            String headerCaller = headers.get(CALLER_META_KEY);
            if (headerCaller != null && !headerCaller.isBlank()) {
                return new WorkloadIdentity(headerCaller.trim().toUpperCase(), "local", Set.of("SERVICE"));
            }
        }
        return null;
    }
}
