package com.umar.integration.api.grpc;

import com.umar.integration.config.HubProperties;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lifecycle-managed Netty gRPC server on port 9089 serving {@link ExtensionGrpcServiceImpl} (INT-13, INT-14).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HubGrpcServer implements SmartLifecycle {

    private final ExtensionGrpcServiceImpl extensionGrpcService;
    private final HubGrpcAuthInterceptor authInterceptor;
    private final HubProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Server server;

    @Override
    public void start() {
        if (!properties.getGrpc().isEnabled() || running.get()) {
            return;
        }
        int port = properties.getGrpc().getPort();
        try {
            NettyServerBuilder builder = NettyServerBuilder.forPort(port)
                    .addService(ServerInterceptors.intercept(extensionGrpcService, authInterceptor));

            if (properties.getGrpc().getSsl().isEnabled()) {
                SslContextBuilder sslBuilder = GrpcSslContexts.forServer(
                                new File(properties.getGrpc().getSsl().getCertChain()),
                                new File(properties.getGrpc().getSsl().getPrivateKey()))
                        .trustManager(new File(properties.getGrpc().getSsl().getTrustCert()))
                        .clientAuth(ClientAuth.REQUIRE);
                builder.sslContext(sslBuilder.build());
            }

            server = builder.build().start();
            running.set(true);
            log.info("Integration Hub gRPC server started on port {} (ssl={})",
                    server.getPort(), properties.getGrpc().getSsl().isEnabled());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start Integration Hub gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            server = null;
        }
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    public int getBoundPort() {
        return server != null ? server.getPort() : properties.getGrpc().getPort();
    }
}
