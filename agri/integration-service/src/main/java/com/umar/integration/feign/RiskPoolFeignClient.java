package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "riskpool-service", url = "${agreeshield.services.riskpool.url:http://localhost:8093}", contextId = "riskPoolFeignClient")
public interface RiskPoolFeignClient {
    @GetMapping("/api/riskpool/accounts/{accountId}")
    Map<String, Object> getAccount(@PathVariable("accountId") String accountId);
}
