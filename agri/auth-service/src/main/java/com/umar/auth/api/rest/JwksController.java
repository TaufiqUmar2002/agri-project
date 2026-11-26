package com.umar.auth.api.rest;

import com.umar.auth.service.AuthAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final AuthAccountService authAccountService;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return authAccountService.getJwks();
    }
}
