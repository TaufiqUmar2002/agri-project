package com.umar.api.rest;

import com.umar.annotation.EnforceVaultPolicy;
import com.umar.payload.request.vault.SignRequest;
import com.umar.payload.response.vault.SignResponse;
import com.umar.service.SecretService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
public class SigningController {

    private final SecretService secretService;

    @PostMapping("/sign")
    @EnforceVaultPolicy(operation = "SIGN")
    public ResponseEntity<SignResponse> signPayload(@RequestBody @Valid SignRequest request) {
        return ResponseEntity.ok(secretService.signPayload(request));
    }
}
