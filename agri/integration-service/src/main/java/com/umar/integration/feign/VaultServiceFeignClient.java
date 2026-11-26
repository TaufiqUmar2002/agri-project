package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "vault-service",
        url = "${agreeshield.services.vault.url:http://localhost:8443}",
        contextId = "vaultServiceFeignClient"
)
public interface VaultServiceFeignClient {

    @PostMapping("/api/vault/encrypt")
    Map<String, String> encrypt(@RequestBody Map<String, String> request);

    @PostMapping("/api/vault/decrypt")
    Map<String, String> decrypt(@RequestBody Map<String, String> request);

    @PostMapping("/api/vault/tokenize")
    Map<String, Object> tokenize(@RequestBody Map<String, Object> request);

    @PostMapping("/api/vault/detokenize")
    Map<String, Object> detokenize(@RequestBody Map<String, Object> request);
}
