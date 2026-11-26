package com.umar.hooks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatches {@code vault.data-class} hook invocations to every registered
 * {@link VaultDataClassHook} bean.
 */
@Slf4j
@Component
public class DataClassHookDispatcher {

    private final List<VaultDataClassHook> hooks;

    public DataClassHookDispatcher(List<VaultDataClassHook> hooks) {
        this.hooks = hooks;
    }

    public void dispatch(DataClassDefinition definition) {
        for (VaultDataClassHook hook : hooks) {
            try {
                hook.contribute(definition);
            } catch (Exception e) {
                log.error("vault.data-class hook {} failed for data class {}",
                        hook.getClass().getName(), definition.getName(), e);
                throw e;
            }
        }
    }
}
