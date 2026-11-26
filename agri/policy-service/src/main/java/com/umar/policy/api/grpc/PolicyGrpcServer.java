package com.umar.policy.api.grpc;

import com.umar.policy.config.PolicyProperties;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyGrpcServer implements SmartLifecycle {

    private final PolicyGrpcServiceImpl serviceImpl;
    private final PolicyGrpcAuthInterceptor authInterceptor;
    private final PolicyProperties properties;

    private Server server;
    private volatile boolean running;

    @Override
    public void start() {
        try {
            server = NettyServerBuilder.forPort(properties.getGrpc().getPort())
                    .addService(ServerInterceptors.intercept(serviceImpl, authInterceptor))
                    .build()
                    .start();
            running = true;
            log.info("Policy gRPC server started on port {}", server.getPort());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start Policy gRPC server", e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return server != null ? server.getPort() : properties.getGrpc().getPort();
    }
}
