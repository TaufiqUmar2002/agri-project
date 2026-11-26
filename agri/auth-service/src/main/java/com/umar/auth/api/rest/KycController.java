package com.umar.auth.api.rest;

import com.umar.auth.service.KycService;
import com.umar.enums.auth.IdType;
import com.umar.enums.auth.KycTier;
import com.umar.payload.request.auth.KycDecisionRequest;
import com.umar.payload.request.auth.SubmitKycRequest;
import com.umar.payload.response.auth.KycDecisionResponse;
import com.umar.payload.response.auth.KycStatusResponse;
import com.umar.payload.response.auth.SubmitKycResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitKycResponse> submitKyc(
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestParam("idType") IdType idType,
            @RequestParam("idNumber") String idNumber,
            @RequestParam("fullNameOnId") String fullNameOnId,
            @RequestParam("dateOfBirth") LocalDate dateOfBirth,
            @RequestParam(value = "targetTier", required = false) KycTier targetTier,
            @RequestPart("documentFront") MultipartFile documentFront,
            @RequestPart(value = "documentBack", required = false) MultipartFile documentBack,
            @RequestPart("selfie") MultipartFile selfie) {
        SubmitKycRequest req = SubmitKycRequest.builder()
                .idType(idType)
                .idNumber(idNumber)
                .fullNameOnId(fullNameOnId)
                .dateOfBirth(dateOfBirth)
                .targetTier(targetTier)
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(kycService.submitKyc(delegationHeader, req, documentFront, documentBack, selfie));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<KycStatusResponse> getKycStatus(@PathVariable("id") UUID kycId) {
        return ResponseEntity.ok(kycService.getKycStatus(kycId));
    }

    @PutMapping("/{id}/decision")
    public ResponseEntity<KycDecisionResponse> decideKyc(
            @PathVariable("id") UUID kycId,
            @Valid @RequestBody KycDecisionRequest request) {
        return ResponseEntity.ok(kycService.decideKycCase(kycId, request));
    }
}
