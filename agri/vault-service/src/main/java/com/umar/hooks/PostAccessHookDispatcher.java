package com.umar.hooks;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Dispatches {@code vault.post-access} signals asynchronously to every registered
 * {@link VaultPostAccessHook} bean. Hook failures and queue saturation are logged
 * but never propagated, so access operations remain non-blocking and unaffected.
 */
@Slf4j
@Component
public class PostAccessHookDispatcher {

    private final List<VaultPostAccessHook> hooks;
    private final ExecutorService executor;

    public PostAccessHookDispatcher(List<VaultPostAccessHook> hooks) {
        this.hooks = Objects.requireNonNullElse(hooks, List.of());
        this.executor = new ThreadPoolExecutor(
                1,
                2,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1024),
                r -> {
                    Thread t = new Thread(r, "vault-post-access-hook");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    public void dispatch(AccessSignal signal) {
        if (hooks.isEmpty() || signal == null) {
            return;
        }
        for (VaultPostAccessHook hook : hooks) {
            executor.execute(() -> {
                try {
                    hook.onAccess(signal);
                } catch (Exception e) {
                    log.error("vault.post-access hook {} failed for signal {}",
                            hook.getClass().getName(), signal, e);
                }
            });
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
