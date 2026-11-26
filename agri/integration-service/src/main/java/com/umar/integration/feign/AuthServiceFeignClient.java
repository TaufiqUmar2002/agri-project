package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "auth-service",
        url = "${agreeshield.services.auth.url:http://localhost:8081}",
        contextId = "authServiceFeignClient"
)
public interface AuthServiceFeignClient {
    @GetMapping("/api/auth/users/{userId}")
    Map<String, Object> getUserContext(@PathVariable("userId") String userId);

    @PostMapping("/api/auth/step-up/verify")
    Map<String, Object> verifyStepUp(@RequestBody Map<String, Object> request);
}
