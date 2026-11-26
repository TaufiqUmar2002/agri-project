package com.umar.riskpool.grpc;

import com.umar.riskpool.config.RiskPoolProperties;
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
public class RiskPoolGrpcServer implements SmartLifecycle {
    private final RiskPoolGrpcServiceImpl serviceImpl;
    private final RiskPoolProperties properties;
    private Server server;
    private volatile boolean running;

    @Override
    public void start() {
        try {
            server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", properties.getGrpc().getPort()))
                    .addService(ServerInterceptors.intercept(serviceImpl, new RiskPoolGrpcAuthInterceptor(properties)))
                    .build().start();
            running = true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start RiskPool gRPC server", e);
        }
    }

    @Override public void stop() { if (server != null) server.shutdown(); running = false; }
    @Override public boolean isRunning() { return running; }
    public int getPort() { return server != null ? server.getPort() : -1; }
}
