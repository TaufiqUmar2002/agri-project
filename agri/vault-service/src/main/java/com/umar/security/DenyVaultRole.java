package com.umar.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Denies access to callers holding any of the listed {@link VaultRole}s
 * (section 2.11 "may never" constraints, e.g. roles that may never see raw values).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DenyVaultRole {

    VaultRole[] roles();
}
