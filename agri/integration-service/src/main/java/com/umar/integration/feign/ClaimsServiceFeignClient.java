package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(
        name = "claims-service",
        url = "${agreeshield.services.claims.url:http://localhost:8087}",
        contextId = "claimsServiceFeignClient"
)
public interface ClaimsServiceFeignClient {
    @GetMapping("/api/claims/{claimId}")
    Map<String, Object> getClaim(@PathVariable("claimId") String claimId);
}
