package com.umar.authentication;


public final class VaultSecurityContext {

    private static final ThreadLocal WORKLOAD_THREAD_LOCAL = new ThreadLocal<>();

    private VaultSecurityContext() {
        // Prevent utility class instantiation
    }

    /**
     * Associates a validated WorkloadIdentity with the current execution thread.
     */
    public static void setWorkloadIdentity(WorkloadIdentity identity) {
        WORKLOAD_THREAD_LOCAL.set(identity);
    }

    /**
     * Extracts the active WorkloadIdentity from the current thread.
     */
    public static WorkloadIdentity getWorkloadIdentity() {
        return (WorkloadIdentity)WORKLOAD_THREAD_LOCAL.get();
    }

    /**
     * Clears the thread context state to prevent cross-request contamination.
     */
    public static void clear() {
        WORKLOAD_THREAD_LOCAL.remove();
    }
}
