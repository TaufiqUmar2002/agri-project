package com.umar.claims.grpc;

import com.umar.claims.config.ClaimsProperties;
import io.grpc.*;

public class ClaimGrpcAuthInterceptor implements ServerInterceptor {

    public static final Context.Key<String> CALLER_ID = Context.key("callerId");
    private static final Metadata.Key<String> TOKEN_KEY = Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> CALLER_KEY = Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER);

    private final ClaimsProperties properties;

    public ClaimGrpcAuthInterceptor(ClaimsProperties properties) {
        this.properties = properties;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String token = headers.get(TOKEN_KEY);
        String caller = headers.get(CALLER_KEY);
        if (token == null || !token.equals(properties.getGrpc().getServiceToken())
                || caller == null || caller.isBlank()) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid service token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        Context ctx = Context.current().withValue(CALLER_ID, caller.trim().toLowerCase());
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
