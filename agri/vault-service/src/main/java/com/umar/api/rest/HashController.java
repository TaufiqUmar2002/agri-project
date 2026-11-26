package com.umar.api.rest;

import com.umar.annotation.EnforceVaultPolicy;
import com.umar.cryptography.HashService;
import com.umar.payload.request.vault.HashRequest;
import com.umar.payload.response.vault.HashResponse;
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
public class HashController {

    private final HashService hashService;

    @PostMapping("/hash")
    @EnforceVaultPolicy(operation = "HASH")
    public ResponseEntity<HashResponse> computeHashes(@RequestBody @Valid HashRequest request) {
        return ResponseEntity.ok(hashService.computeHashes(request));
    }
}
