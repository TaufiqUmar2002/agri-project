package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "audit-service",
        url = "${agreeshield.services.audit.url:http://localhost:8092}",
        contextId = "auditServiceFeignClient"
)
public interface AuditServiceFeignClient {
    @GetMapping("/api/audit/decisions/{claimId}")
    Map<String, Object> getDecisionTrace(@PathVariable("claimId") String claimId);

    @PostMapping("/api/audit/holds")
    Map<String, Object> manageHold(@RequestBody Map<String, Object> request);
}
