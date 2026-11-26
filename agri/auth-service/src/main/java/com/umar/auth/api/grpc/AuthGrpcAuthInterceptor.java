package com.umar.auth.api.grpc;

import com.umar.auth.config.AuthProperties;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthGrpcAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> SERVICE_IDENTITY_KEY =
            Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER);

    private final AuthProperties properties;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String caller = headers.get(SERVICE_IDENTITY_KEY);
        if (caller == null || caller.isBlank()) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing service identity"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        String normalized = caller.trim().toLowerCase();
        boolean allowed = properties.getSecurity().getAllowedGrpcConsumers().stream()
                .anyMatch(c -> c.equalsIgnoreCase(normalized));
        if (!allowed) {
            call.close(Status.PERMISSION_DENIED.withDescription("Consumer not allowed: " + caller), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}
