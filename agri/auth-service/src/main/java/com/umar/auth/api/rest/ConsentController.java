package com.umar.auth.api.rest;

import com.umar.auth.service.ConsentService;
import com.umar.payload.request.auth.RecordConsentRequest;
import com.umar.payload.request.auth.WithdrawConsentRequest;
import com.umar.payload.response.auth.RecordConsentResponse;
import com.umar.payload.response.auth.WithdrawConsentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @PostMapping
    public ResponseEntity<RecordConsentResponse> recordConsent(
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @Valid @RequestBody RecordConsentRequest request) {
        ConsentService.RecordResult res = consentService.recordConsent(delegationHeader, request);
        return ResponseEntity.status(res.createdAny() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(res.response());
    }

    @PostMapping("/{consentId}/withdraw")
    public ResponseEntity<WithdrawConsentResponse> withdrawConsent(
            @PathVariable("consentId") UUID consentId,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestHeader(value = "X-Step-Up-Token") String stepUpToken,
            @Valid @RequestBody WithdrawConsentRequest request) {
        return ResponseEntity.ok(
                consentService.withdrawConsent(consentId, delegationHeader, stepUpToken, request));
    }
}
