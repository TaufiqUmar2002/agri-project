package com.umar.hooks;

/**
 * Extension point {@code vault.data-class} (section 2.16).
 *
 * Platform teams implement this interface as a Spring bean to contribute
 * validation and masking rules when a new data class is being defined. All
 * registered hooks are invoked, in order, before the data class is persisted.
 */
public interface VaultDataClassHook {

    /**
     * Contribute to the data class definition. Implementations may read the
     * current definition and set/override {@code validation} and
     * {@code maskingRules} (and other definable fields) on the context.
     *
     * @param definition the mutable data class definition being built
     */
    void contribute(DataClassDefinition definition);
}
