package com.umar.security;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link DenyVaultRole} by rejecting callers that hold any denied role.
 */
@Aspect
@Component
@Order(3)
@RequiredArgsConstructor
public class VaultDenyRoleAspect {

    private final RoleAuthorizationService roleAuthorizationService;

    @Around("@annotation(denyVaultRole)")
    public Object enforceDeny(ProceedingJoinPoint joinPoint, DenyVaultRole denyVaultRole) throws Throwable {
        roleAuthorizationService.denyAnyRole(denyVaultRole.roles());
        return joinPoint.proceed();
    }
}
