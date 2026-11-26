package com.umar.security;

import com.umar.exception.PolicyViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Enforces {@link RequireVaultRole} by checking the caller's roles against the
 * operation's allowed roles from {@link VaultOperationRoles}.
 */
@Slf4j
@Aspect
@Component
@Order(2)
@RequiredArgsConstructor
public class VaultRoleAspect {

    private final RoleAuthorizationService roleAuthorizationService;

    @Around("@annotation(requireVaultRole)")
    public Object enforceRole(ProceedingJoinPoint joinPoint, RequireVaultRole requireVaultRole) throws Throwable {
        if (AgreeShieldSecurityContext.current().securityBypassed()) {
            return joinPoint.proceed();
        }
        String operation = requireVaultRole.operation();
        Set<VaultRole> allowed = VaultOperationRoles.allowedRoles(operation);
        if (allowed.isEmpty()) {
            log.warn("No role mapping for operation {}; skipping role enforcement", operation);
            return joinPoint.proceed();
        }
        roleAuthorizationService.requireAnyRole(allowed.toArray(new VaultRole[0]));
        return joinPoint.proceed();
    }
}
