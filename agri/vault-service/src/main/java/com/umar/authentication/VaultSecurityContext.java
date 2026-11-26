package com.umar.authentication;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public final class VaultSecurityContext {

    private static final ThreadLocal<WorkloadIdentity> WORKLOAD_THREAD_LOCAL = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> STRONG_AUTH_THREAD_LOCAL = new ThreadLocal<>();

    @Setter
    private static volatile boolean devFallbackEnabled = true;

    private VaultSecurityContext() {
    }

    public static void setWorkloadIdentity(WorkloadIdentity identity) {
        log.debug("SET on thread [{}] -> {}", Thread.currentThread().getName(), identity);
        WORKLOAD_THREAD_LOCAL.set(identity);
    }

    public static void setStrongAuth(boolean strongAuth) {
        STRONG_AUTH_THREAD_LOCAL.set(strongAuth);
    }

    public static boolean isStrongAuth() {
        Boolean val = STRONG_AUTH_THREAD_LOCAL.get();
        return Boolean.TRUE.equals(val);
    }

    public static WorkloadIdentity getWorkloadIdentity() {
        WorkloadIdentity identity = WORKLOAD_THREAD_LOCAL.get();
        if (identity == null && devFallbackEnabled) {
            return new WorkloadIdentity("TEMP-SERVICE", "local", Set.of("SERVICE", "MAKER", "VIEWER"));
        }
        return identity;
    }

    public static WorkloadIdentity getRawWorkloadIdentity() {
        return WORKLOAD_THREAD_LOCAL.get();
    }

    public static void clear() {
        WORKLOAD_THREAD_LOCAL.remove();
        STRONG_AUTH_THREAD_LOCAL.remove();
    }
}
