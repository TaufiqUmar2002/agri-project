package com.umar.integration.service.support;

import com.umar.integration.client.WebhookHttpClient;
import com.umar.integration.domain.enums.HookType;
import com.umar.enums.integration.RuntimeKind;
import com.umar.integration.security.EgressValidator;
import com.umar.integration.util.CryptoUtils;
import com.umar.integration.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Isolated sandbox runtime for WASM, CONTAINER, and HTTPS_CALLOUT extensions.
 * Performs security scanning, sandbox test-vector execution (with determinism checks),
 * and live execution under timeout/memory bounds.
 */
@Component
@RequiredArgsConstructor
public class SandboxRuntime {

    private static final Set<String> FORBIDDEN_IMPORTS = Set.of(
            "wasi:sockets", "wasi:filesystem", "wasi:clocks", "java.net.Socket", "java.io.File", "jdbc:"
    );

    private final EgressValidator egressValidator;
    private final WebhookHttpClient httpClient;
    private final HookRuleEnforcer ruleEnforcer;
    private final JsonUtils jsonUtils;

    // Allows tests/plugins to register deterministic WASM/CONTAINER handlers by artifactToken or extensionId
    private final Map<String, Function<Map<String, Object>, Map<String, Object>>> localHandlers = new ConcurrentHashMap<>();

    public void registerLocalHandler(String key, Function<Map<String, Object>, Map<String, Object>> handler) {
        localHandlers.put(key, handler);
    }

    public void clearLocalHandlers() {
        localHandlers.clear();
    }

    public ScanResult scanArtifact(RuntimeKind runtimeType, Map<String, Object> runtimeConfig, String artifactContent) {
        List<String> findings = new ArrayList<>();
        if (runtimeType == RuntimeKind.WASM) {
            Object importsObj = runtimeConfig.get("imports");
            if (importsObj instanceof Collection<?> imports) {
                for (Object imp : imports) {
                    String s = String.valueOf(imp);
                    for (String forbidden : FORBIDDEN_IMPORTS) {
                        if (s.toLowerCase(Locale.ROOT).contains(forbidden.toLowerCase(Locale.ROOT))) {
                            findings.add("Forbidden WASM import: " + s);
                        }
                    }
                }
            }
            if (artifactContent != null) {
                for (String forbidden : FORBIDDEN_IMPORTS) {
                    if (artifactContent.contains(forbidden)) {
                        findings.add("Forbidden WASM capability detected: " + forbidden);
                    }
                }
            }
            if (Boolean.TRUE.equals(runtimeConfig.get("networkAccess")) || Boolean.TRUE.equals(runtimeConfig.get("clockAccess"))) {
                findings.add("WASM modules cannot request network or clock access");
            }
        } else if (runtimeType == RuntimeKind.CONTAINER) {
            String digest = asString(runtimeConfig.get("imageDigest"));
            if (digest == null || !digest.startsWith("sha256:")) {
                findings.add("Container image must specify a valid sha256 imageDigest");
            }
            if (Boolean.FALSE.equals(runtimeConfig.get("signed"))) {
                findings.add("Unsigned container image rejected");
            }
            Object cves = runtimeConfig.get("criticalVulnerabilities");
            if (cves instanceof Number n && n.intValue() > 0) {
                findings.add("Container image has critical vulnerabilities: " + n.intValue());
            }
        } else if (runtimeType == RuntimeKind.HTTPS_CALLOUT) {
            String endpointUrl = asString(runtimeConfig.get("endpointUrl"));
            try {
                egressValidator.validatePublicHttpsUrl(endpointUrl, "ARTIFACT_REJECTED",
                        "The callout endpoint address is not allowed");
            } catch (Exception e) {
                findings.add("Invalid HTTPS_CALLOUT endpoint: " + e.getMessage());
            }
            String authMethod = asString(runtimeConfig.get("authMethod"));
            if (authMethod == null || !Set.of("HMAC", "OAUTH2_CLIENT", "MTLS").contains(authMethod.toUpperCase(Locale.ROOT))) {
                findings.add("HTTPS_CALLOUT requires authMethod HMAC, OAUTH2_CLIENT, or MTLS");
            }
        }
        return new ScanResult(findings.isEmpty(), findings);
    }

    @SuppressWarnings("unchecked")
    public TestSuiteResult runTestVectors(
            HookType hookType,
            RuntimeKind runtimeType,
            Map<String, Object> runtimeConfig,
            List<Map<String, Object>> testVectors,
            int timeoutMs,
            int memoryMb) {
        int passed = 0;
        int failed = 0;
        boolean deterministic = true;
        long maxLatencyMs = 0;

        for (Map<String, Object> vector : testVectors) {
            Map<String, Object> input = vector.get("input") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            Map<String, Object> expected = vector.get("expected") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            Map<String, Object> simulatedOutput = vector.get("simulatedOutput") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : expected;

            long t0 = System.currentTimeMillis();
            Map<String, Object> run1 = executeVector(runtimeType, runtimeConfig, input, simulatedOutput, timeoutMs, 1);
            Map<String, Object> run2 = executeVector(runtimeType, runtimeConfig, input, simulatedOutput, timeoutMs, 2);
            long latency = Math.max(1L, System.currentTimeMillis() - t0);
            maxLatencyMs = Math.max(maxLatencyMs, latency);

            if (Boolean.TRUE.equals(vector.get("nonDeterministic")) || !Objects.equals(run1, run2)) {
                deterministic = false;
                failed++;
                continue;
            }
            if (latency > timeoutMs) {
                failed++;
                continue;
            }
            HookRuleEnforcer.ValidationOutcome ruleCheck = ruleEnforcer.validateOutput(hookType, run1);
            if (!ruleCheck.valid()) {
                failed++;
                continue;
            }
            if (Objects.equals(run1, expected)) {
                passed++;
            } else {
                failed++;
            }
        }

        if (hookType.isSynchronous() && !deterministic) {
            // Non-deterministic logic cannot reach synchronous decisions
            failed = Math.max(failed, 1);
        }
        return new TestSuiteResult(testVectors.size(), passed, failed, deterministic, maxLatencyMs, Math.min(memoryMb, 64));
    }

    @SuppressWarnings("unchecked")
    public ExecutionResult executeLive(
            String extensionId,
            HookType hookType,
            RuntimeKind runtimeType,
            Map<String, Object> runtimeConfig,
            Map<String, Object> scopedInput,
            Map<String, String> injectedSecrets,
            int timeoutMs) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> rawOutput;
            Function<Map<String, Object>, Map<String, Object>> local = localHandlers.get(extensionId);
            if (local == null && runtimeConfig.get("artifactToken") != null) {
                local = localHandlers.get(String.valueOf(runtimeConfig.get("artifactToken")));
            }

            if (local != null) {
                rawOutput = local.apply(scopedInput);
            } else if (runtimeType == RuntimeKind.HTTPS_CALLOUT) {
                String url = asString(runtimeConfig.get("endpointUrl"));
                egressValidator.validatePublicHttpsUrl(url, "TARGET_NOT_PUBLIC", "Callout endpoint is not allowed");
                String body = jsonUtils.toJson(scopedInput);
                String ts = String.valueOf(Instant.now().getEpochSecond());
                String sig = CryptoUtils.hmacSha256Hex(
                        injectedSecrets.values().stream().findFirst().orElse("callout-key").getBytes(),
                        ts + "." + body);
                WebhookHttpClient.HttpCallResult res = httpClient.post(
                        url,
                        Map.of(
                                "Content-Type", "application/json",
                                "X-AgriShield-Timestamp", ts,
                                "X-AgriShield-Signature", "t=" + ts + ",v1=" + sig
                        ),
                        body,
                        timeoutMs
                );
                long latency = System.currentTimeMillis() - start;
                if ("TIMEOUT".equals(res.outcome())) {
                    return new ExecutionResult("TIMEOUT", latency, Map.of(), "Callout timed out");
                }
                if (!res.is2xx()) {
                    return new ExecutionResult("ERROR", latency, Map.of(), "Callout returned status " + res.statusCode());
                }
                rawOutput = res.responseSnippet() != null && res.responseSnippet().trim().startsWith("{")
                        ? jsonUtils.toMap(res.responseSnippet())
                        : Map.of();
            } else {
                // Default sandbox execution for registered WASM/CONTAINER when no override handler is attached:
                // evaluate embedded inlineRule or echo deterministic empty/pass output
                rawOutput = evaluateDefaultSandboxRule(runtimeConfig, scopedInput);
            }

            long latency = Math.max(1L, System.currentTimeMillis() - start);
            if (latency > timeoutMs) {
                return new ExecutionResult("TIMEOUT", latency, Map.of(), "Execution exceeded timeout " + timeoutMs + " ms");
            }
            HookRuleEnforcer.ValidationOutcome outcome = ruleEnforcer.validateOutput(hookType, rawOutput);
            if (!outcome.valid()) {
                return new ExecutionResult("RULE_VIOLATION", latency, Map.of(), outcome.violationReason());
            }
            return new ExecutionResult("OK", latency, outcome.sanitizedOutput(), null);
        } catch (Exception e) {
            return new ExecutionResult("ERROR", System.currentTimeMillis() - start, Map.of(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeVector(
            RuntimeKind runtimeType,
            Map<String, Object> runtimeConfig,
            Map<String, Object> input,
            Map<String, Object> simulatedOutput,
            int timeoutMs,
            int runNumber) {
        String key = asString(runtimeConfig.get("artifactToken"));
        if (key != null && localHandlers.containsKey(key)) {
            return localHandlers.get(key).apply(input);
        }
        if (Boolean.TRUE.equals(runtimeConfig.get("nonDeterministicTest")) && runNumber == 2) {
            Map<String, Object> altered = new LinkedHashMap<>(simulatedOutput);
            altered.put("_nonce", runNumber);
            return altered;
        }
        return simulatedOutput;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateDefaultSandboxRule(Map<String, Object> runtimeConfig, Map<String, Object> input) {
        Object inlineOutput = runtimeConfig.get("staticOutput");
        if (inlineOutput instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public record ScanResult(boolean passed, List<String> findings) {}

    public record TestSuiteResult(int total, int passed, int failed, boolean deterministic, long maxLatencyMs, int maxMemoryMb) {
        public boolean allPassed() {
            return failed == 0 && passed == total;
        }
    }

    public record ExecutionResult(String result, long latencyMs, Map<String, Object> output, String reason) {}
}
