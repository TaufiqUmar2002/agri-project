package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "payout-service", url = "${agreeshield.services.payout.url:http://localhost:8088}", contextId = "payoutServiceFeignClient")
public interface PayoutServiceFeignClient {
    @GetMapping("/api/payouts/{payoutId}")
    Map<String, Object> getPayout(@PathVariable("payoutId") String payoutId);
}
