package com.umar.hooks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default {@code vault.post-access} hook that logs every access signal. Serves
 * as a reference implementation and baseline observability; external security
 * tools should register their own {@link VaultPostAccessHook} beans.
 */
@Slf4j
@Component
public class LoggingPostAccessHook implements VaultPostAccessHook {

    @Override
    public void onAccess(AccessSignal signal) {
        log.info("vault.post-access signal: caller={}, operation={}, dataClass={}, purpose={}, outcome={}, records={}",
                signal.caller(), signal.operation(), signal.dataClass(), signal.purpose(),
                signal.outcome(), signal.recordCount());
    }
}
