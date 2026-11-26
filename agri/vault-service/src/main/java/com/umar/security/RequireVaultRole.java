package com.umar.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces section 2.11 role-based authorization on a controller method. The
 * {@link #operation()} must have a role mapping in {@link VaultOperationRoles}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireVaultRole {

    /** The vault operation whose allowed roles gate this method. */
    String operation();
}
