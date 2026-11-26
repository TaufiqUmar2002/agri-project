package com.umar.policy.api.rest;

import com.umar.payload.request.policy.CreateProductRequest;
import com.umar.payload.request.policy.ProductDecisionRequest;
import com.umar.payload.response.policy.EligibleProductsResponse;
import com.umar.payload.response.policy.ProductVersionResponse;
import com.umar.policy.service.ProductGovernanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductGovernanceService productService;

    @PostMapping
    public ResponseEntity<ProductVersionResponse> createProductVersion(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProductVersion(request));
    }

    @PutMapping("/{productId}/versions/{version}/decision")
    public ResponseEntity<ProductVersionResponse> decideProductVersion(
            @PathVariable String productId,
            @PathVariable int version,
            @Valid @RequestBody ProductDecisionRequest request) {
        return ResponseEntity.ok(productService.decideProductVersion(productId, version, request));
    }

    @GetMapping("/eligible")
    public ResponseEntity<EligibleProductsResponse> listEligibleProducts(
            @RequestParam String plotId,
            @RequestParam(required = false) String farmerId,
            @RequestParam(required = false) String language,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader) {
        return ResponseEntity.ok(productService.listEligibleProducts(plotId, farmerId, language, delegationHeader));
    }
}
