package com.umar.farmer.api.grpc;

import com.umar.farmer.config.FarmerProperties;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FarmerGrpcAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> SERVICE_IDENTITY_KEY =
            Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER);
    static final Context.Key<String> CALLER_CTX_KEY = Context.key("callerService");

    private final FarmerProperties properties;

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
            call.close(Status.PERMISSION_DENIED.withDescription("Consumer not allowed"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        Context ctx = Context.current().withValue(CALLER_CTX_KEY, normalized);
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
