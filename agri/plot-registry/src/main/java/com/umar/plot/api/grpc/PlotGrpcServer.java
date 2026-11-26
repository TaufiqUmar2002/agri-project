package com.umar.plot.api.grpc;

import com.umar.plot.config.PlotProperties;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlotGrpcServer implements SmartLifecycle {

    private final PlotGrpcServiceImpl serviceImpl;
    private final PlotGrpcAuthInterceptor authInterceptor;
    private final PlotProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Server server;

    @Override
    public void start() {
        if (!properties.getGrpc().isEnabled() || running.get()) return;
        try {
            server = NettyServerBuilder.forPort(properties.getGrpc().getPort())
                    .addService(ServerInterceptors.intercept(serviceImpl, authInterceptor))
                    .build()
                    .start();
            running.set(true);
            log.info("Plot Registry gRPC server started on port {}", server.getPort());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start Plot gRPC server", e);
        }
    }

    @Override
    public void stop() {
        if (server != null) { server.shutdown(); server = null; }
        running.set(false);
    }

    @Override
    public boolean isRunning() { return running.get(); }

    public int getBoundPort() { return server != null ? server.getPort() : properties.getGrpc().getPort(); }
}
