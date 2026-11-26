package com.umar.plot.api.grpc;

import com.umar.plot.config.PlotProperties;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlotGrpcAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> SERVICE_IDENTITY_KEY =
            Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER);
    static final Context.Key<String> CALLER_CTX_KEY = Context.key("callerService");

    private final PlotProperties properties;

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
        String norm = caller.trim().toLowerCase();
        boolean allowed = properties.getSecurity().getAllowedGrpcConsumers().stream()
                .anyMatch(c -> c.equalsIgnoreCase(norm));
        if (!allowed) {
            call.close(Status.PERMISSION_DENIED.withDescription("Consumer not allowed"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return Contexts.interceptCall(Context.current().withValue(CALLER_CTX_KEY, norm), call, headers, next);
    }
}
