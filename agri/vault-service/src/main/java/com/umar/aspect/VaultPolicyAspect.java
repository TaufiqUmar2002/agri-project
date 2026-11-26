package com.umar.aspect;

import com.umar.annotation.EnforceVaultPolicy;
import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.exception.PolicyViolationException;
import com.umar.policy.PolicyResource;
import com.umar.policy.PolicyResourceResolver;
import com.umar.policy.VaultPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Slf4j
@Aspect
@Component
@Order(value = 1)
@RequiredArgsConstructor
public class VaultPolicyAspect {

    private final VaultPolicyService vaultPolicyService;
    private final List<PolicyResourceResolver> resolvers;

    @Around("@annotation(enforceVaultPolicy)")
    public Object enforcePolicy(ProceedingJoinPoint joinPoint, EnforceVaultPolicy enforceVaultPolicy) throws Throwable {
        log.info("Enforcing vault policy for operation: {}", enforceVaultPolicy.operation());
        WorkloadIdentity caller = VaultSecurityContext.getWorkloadIdentity();
        if (caller == null) {
            throw com.umar.exception.VaultApiException.unauthorized("IDENTITY_NOT_ACCEPTED", "No valid service identity on the connection");
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        var resolverOpt = resolvers.stream()
                .filter(candidate -> candidate.supports(method))
                .findFirst();
        if (resolverOpt.isEmpty()) {
            throw new PolicyViolationException("Access Denied: No policy resource resolver registered for method " + method.getName());
        }

        // 1. Extract requested data classes and purposes.
        List<PolicyResource> resources = resolverOpt.get().resolve(joinPoint.getArgs());
        // 2. Verify service identity and access policies.
        vaultPolicyService.authorize(enforceVaultPolicy.operation(), resources);
        // 3. Execute API only after successful authorization.
        return joinPoint.proceed();
    }
}
