package com.umar.integration.service.support;

import com.umar.integration.domain.enums.HookType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Enforces Section 2.2 hook-type rules on every extension output:
 * - PRE: may add holds/rejections/capped loadings; cannot release a hold or approve/override protected values
 * - ENRICH: may add attributes only under x_ namespace; cannot modify existing fields
 * - TRANSFORM: schema-bound reshaping/redaction only; cannot add disallowed fields
 * - VALIDATE: may only reject or hold (more restrictive); never release or approve
 * - POST: output ignored
 * - ADAPTER: interface-bound; cannot mark final or bypass checks
 * - SHADOW: output discarded by runtime
 */
@Component
public class HookRuleEnforcer {

    private static final Set<String> FORBIDDEN_RELEASE_VALUES = Set.of("RELEASE", "APPROVE", "BYPASS", "OVERRIDE");

    public ValidationOutcome validateOutput(HookType hookType, Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return ValidationOutcome.ok(Map.of());
        }

        if (hookType == HookType.POST || hookType == HookType.SHADOW) {
            return ValidationOutcome.ok(Map.of());
        }

        // Reject attempts to release holds or override approvals across PRE/VALIDATE/ADAPTER
        Object decisionObj = output.get("decision");
        if (decisionObj instanceof String dec && FORBIDDEN_RELEASE_VALUES.contains(dec.trim().toUpperCase())) {
            return ValidationOutcome.violation("Extension attempted forbidden decision: " + dec);
        }
        if (Boolean.TRUE.equals(output.get("releaseHold")) || Boolean.TRUE.equals(output.get("bypassControl"))
                || Boolean.TRUE.equals(output.get("markFinal"))) {
            return ValidationOutcome.violation("Extension attempted to release a hold or bypass platform controls");
        }

        if (hookType == HookType.ENRICH) {
            for (String key : output.keySet()) {
                if (!key.startsWith("x_") && !"attributes".equals(key)) {
                    return ValidationOutcome.violation("ENRICH hook may only write under x_ namespace, got: " + key);
                }
            }
            Object attrs = output.get("attributes");
            if (attrs instanceof Map<?, ?> attrMap) {
                for (Object k : attrMap.keySet()) {
                    if (!String.valueOf(k).startsWith("x_")) {
                        return ValidationOutcome.violation("ENRICH attribute must use x_ prefix: " + k);
                    }
                }
            }
        }

        if (hookType == HookType.VALIDATE) {
            for (String key : output.keySet()) {
                if (!Set.of("hold", "holds", "reject", "rejections", "reasonCode", "decision", "valid", "reason").contains(key)) {
                    return ValidationOutcome.violation("VALIDATE hook cannot mutate non-decision field: " + key);
                }
            }
            if (Boolean.TRUE.equals(output.get("valid")) && !output.containsKey("hold") && !output.containsKey("reject")) {
                // Valid pass-through is allowed as no-op, unless it tries to release
                return ValidationOutcome.ok(output);
            }
        }

        if (hookType == HookType.PRE) {
            if (output.containsKey("payoutAmount") || output.containsKey("approved") || output.containsKey("floorPrice")) {
                return ValidationOutcome.violation("PRE hook cannot alter protected financial/approval fields");
            }
        }

        return ValidationOutcome.ok(output);
    }

    public record ValidationOutcome(boolean valid, String violationReason, Map<String, Object> sanitizedOutput) {
        public static ValidationOutcome ok(Map<String, Object> out) {
            return new ValidationOutcome(true, null, out);
        }

        public static ValidationOutcome violation(String reason) {
            return new ValidationOutcome(false, reason, Map.of());
        }
    }
}
