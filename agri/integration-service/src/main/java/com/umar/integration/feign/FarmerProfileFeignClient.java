package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(
        name = "farmer-profile",
        url = "${agreeshield.services.farmer.url:http://localhost:8082}",
        contextId = "farmerProfileFeignClient"
)
public interface FarmerProfileFeignClient {
    @GetMapping("/api/farmers/{farmerId}")
    Map<String, Object> getFarmer(@PathVariable("farmerId") String farmerId);
}
