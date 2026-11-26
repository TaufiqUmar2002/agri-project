package com.umar.claims.grpc;

import com.umar.claims.config.ClaimsProperties;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;

@Component
@RequiredArgsConstructor
public class ClaimGrpcServer implements SmartLifecycle {

    private final ClaimGrpcServiceImpl serviceImpl;
    private final ClaimsProperties properties;
    private Server server;
    private volatile boolean running;

    @Override
    public void start() {
        try {
            server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", properties.getGrpc().getPort()))
                    .addService(ServerInterceptors.intercept(serviceImpl, new ClaimGrpcAuthInterceptor(properties)))
                    .build()
                    .start();
            running = true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start Claims gRPC server", e);
        }
    }

    @Override
    public void stop() {
        if (server != null) server.shutdown();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return server != null ? server.getPort() : -1;
    }
}
