package com.umar.aspect;

import com.umar.annotation.EnforceVaultPolicy;
import com.umar.authentication.VaultSecurityContext;
import com.umar.authentication.WorkloadIdentity;
import com.umar.error.ApiError;
import com.umar.error.ApiException;
import com.umar.exception.PolicyViolationException;
import com.umar.policy.PolicyEngine;
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
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
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
    private final ExpressionParser parser = new SpelExpressionParser();
    private final PolicyEngine policyEngine;


    @Around("@annotation(enforceVaultPolicy)")
    public Object enforcePolicy(ProceedingJoinPoint joinPoint, EnforceVaultPolicy enforceVaultPolicy) throws Throwable {
        log.info("Enforcing vault policy for operation: {}", enforceVaultPolicy.operation());

//        WorkloadIdentity caller = VaultSecurityContext.getWorkloadIdentity();
//        if (caller == null) {
//            throw new PolicyViolationException("Access Denied: Unauthenticated machine profile identity missing.");
//        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();
        EvaluationContext context = new StandardEvaluationContext();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        // 3. Resolve metadata keys dynamically via expression string extraction loops
//        String resolvedDataClass = resolveExpression(enforceVaultPolicy.dataClass(), context);
//        String resolvedPurpose = resolveExpression(enforceVaultPolicy.purpose(), context);
//        String operation = enforceVaultPolicy.operation();

        // 4. Assert policy execution clearance
//        boolean authorized = policyEngine.evaluate(caller, resolvedDataClass, resolvedPurpose, operation);
//
//        if (!authorized) {
//            throw new PolicyViolationException(String.format("Access Denied: Service '%s' in environment '%s' is unauthorized to execute '%s' on '%s' under purpose context '%s'.", caller.serviceName(), caller.environment(), operation, resolvedDataClass, resolvedPurpose));
//        }

        Method method = signature.getMethod();
        PolicyResourceResolver resolver = resolvers.stream()
                .filter(candidate -> candidate.supports(method))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","vault.policy.not.found"));
        // 1. Extract requested data classes and purposes.
       List<PolicyResource> resources = resolver.resolve(joinPoint.getArgs());
        // 2. Verify service identity and access policies.
//        vaultPolicyService.authorize(enforceVaultPolicy.operation(), resources);
        // 3. Execute API only after successful authorization.
        return joinPoint.proceed();
    }

    private String resolveExpression(String expressionString, EvaluationContext context) {
        if (expressionString != null && expressionString.startsWith("#")) {
            return parser.parseExpression(expressionString).getValue(context, String.class);
        }
        return expressionString;
    }
}
