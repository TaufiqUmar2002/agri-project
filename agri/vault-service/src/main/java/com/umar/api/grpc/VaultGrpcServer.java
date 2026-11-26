package com.umar.api.grpc;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lifecycle-managed Netty gRPC server on port 9443 serving {@link VaultGrpcServiceImpl} (VLT-11),
 * with optional mTLS certificate enforcement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VaultGrpcServer implements SmartLifecycle {

    private final VaultGrpcServiceImpl vaultGrpcService;
    private final GrpcMtlsAuthInterceptor authInterceptor;

    @Value("${vault.grpc.enabled:true}")
    private boolean enabled;

    @Value("${vault.grpc.port:9443}")
    private int port;

    @Value("${vault.grpc.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${vault.grpc.ssl.cert-chain:certs/server/vault-server.crt}")
    private String certChainPath;

    @Value("${vault.grpc.ssl.private-key:certs/server/vault-server.key}")
    private String privateKeyPath;

    @Value("${vault.grpc.ssl.trust-cert:certs/ca/vault-ca.crt}")
    private String trustCertPath;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Server server;

    @Override
    public void start() {
        if (!enabled || running.get()) {
            return;
        }
        try {
            NettyServerBuilder builder = NettyServerBuilder.forPort(port)
                    .addService(ServerInterceptors.intercept(vaultGrpcService, authInterceptor));

            if (sslEnabled) {
                SslContextBuilder sslBuilder = GrpcSslContexts.forServer(
                                new File(certChainPath), new File(privateKeyPath))
                        .trustManager(new File(trustCertPath))
                        .clientAuth(ClientAuth.REQUIRE);
                builder.sslContext(sslBuilder.build());
            }

            server = builder.build().start();
            running.set(true);
            log.info("Vault gRPC server started on port {} (ssl={})", server.getPort(), sslEnabled);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start Vault gRPC server on port " + port, e);
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
        return server != null ? server.getPort() : port;
    }
}
