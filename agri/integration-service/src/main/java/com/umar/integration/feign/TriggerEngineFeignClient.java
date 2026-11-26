package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "trigger-engine", url = "${agreeshield.services.trigger.url:http://localhost:8086}", contextId = "triggerEngineFeignClient")
public interface TriggerEngineFeignClient {
    @GetMapping("/api/triggers/decisions/{decisionId}")
    Map<String, Object> getDecision(@PathVariable("decisionId") String decisionId);
}
