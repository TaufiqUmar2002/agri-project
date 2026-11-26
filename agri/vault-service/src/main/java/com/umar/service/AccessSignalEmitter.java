package com.umar.service;

import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.hooks.AccessSignal;
import com.umar.hooks.PostAccessHookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Emits {@code vault.post-access} signals after protected vault operations.
 * Centralizes caller resolution and dispatch so access services stay clean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessSignalEmitter {

    private final PostAccessHookDispatcher dispatcher;

    public void emit(String operation, String dataClass, String purpose, String outcome, int recordCount) {
        WorkloadIdentity identity = VaultSecurityContext.getWorkloadIdentity();
        String caller = identity != null ? identity.serviceName() : "UNKNOWN";
        AccessSignal signal = AccessSignal.of(caller, operation, dataClass, purpose, outcome, recordCount);
        dispatcher.dispatch(signal);
    }
}
