package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(
        name = "policy-service",
        url = "${agreeshield.services.policy.url:http://localhost:8084}",
        contextId = "policyServiceFeignClient"
)
public interface PolicyServiceFeignClient {
    @GetMapping("/api/policies/{policyId}")
    Map<String, Object> getPolicy(@PathVariable("policyId") String policyId);
}
