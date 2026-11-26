package com.umar.policy.api.grpc;

import com.umar.policy.config.PolicyProperties;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PolicyGrpcAuthInterceptor implements ServerInterceptor {

    public static final Context.Key<String> CALLER_SERVICE = Context.key("caller-service");
    private static final Metadata.Key<String> TOKEN_KEY = Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> CALLER_KEY = Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER);

    private final PolicyProperties properties;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String token = headers.get(TOKEN_KEY);
        String caller = headers.get(CALLER_KEY);
        if (token == null || !token.equals(properties.getGrpc().getInternalToken())
                || caller == null || caller.isBlank()) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid service credentials"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        if (!properties.getGrpc().getAllowedCallers().contains(caller)) {
            call.close(Status.PERMISSION_DENIED.withDescription("Caller not allowed: " + caller), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        Context ctx = Context.current().withValue(CALLER_SERVICE, caller);
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
