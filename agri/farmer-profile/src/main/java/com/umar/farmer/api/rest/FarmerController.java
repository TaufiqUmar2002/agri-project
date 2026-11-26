package com.umar.farmer.api.rest;

import com.umar.enums.farmer.FarmerStatus;
import com.umar.farmer.service.FarmerProfileService;
import com.umar.farmer.service.WalletService;
import com.umar.payload.request.farmer.CreateFarmerProfileRequest;
import com.umar.payload.request.farmer.RegisterWalletRequest;
import com.umar.payload.request.farmer.ReviewWalletRequest;
import com.umar.payload.request.farmer.UpdateFarmerProfileRequest;
import com.umar.payload.response.farmer.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/farmers")
@RequiredArgsConstructor
public class FarmerController {

    private final FarmerProfileService profileService;
    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<FarmerProfileResponse> createProfile(
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateFarmerProfileRequest request) {
        FarmerProfileResponse resp = profileService.createProfile(delegationHeader, stepUpToken, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + resp.getVersion() + "\"")
                .body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FarmerProfileViewResponse> getProfile(
            @PathVariable("id") UUID farmerId,
            @RequestParam(value = "include", required = false) String include,
            @RequestParam(value = "payoutLimit", defaultValue = "10") int payoutLimit,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader) {
        FarmerProfileViewResponse resp = profileService.getProfile(farmerId, include, payoutLimit, delegationHeader);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + resp.getVersion() + "\"")
                .body(resp);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FarmerProfileResponse> updateProfile(
            @PathVariable("id") UUID farmerId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @Valid @RequestBody UpdateFarmerProfileRequest request) {
        FarmerProfileResponse resp = profileService.updateProfile(farmerId, ifMatch, delegationHeader, request);
        return ResponseEntity.ok()
                .eTag("\"" + resp.getVersion() + "\"")
                .body(resp);
    }

    @PostMapping("/{id}/wallets")
    public ResponseEntity<WalletResponse> registerWallet(
            @PathVariable("id") UUID farmerId,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestHeader("X-Step-Up-Token") String stepUpToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RegisterWalletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walletService.registerOrChangeWallet(farmerId, delegationHeader, stepUpToken, idempotencyKey, request));
    }

    @PutMapping("/{id}/wallets/{walletId}/review")
    public ResponseEntity<ReviewWalletResponse> reviewWallet(
            @PathVariable("id") UUID farmerId,
            @PathVariable("walletId") UUID walletId,
            @Valid @RequestBody ReviewWalletRequest request) {
        return ResponseEntity.ok(walletService.reviewWallet(farmerId, walletId, request));
    }

    @GetMapping("/search")
    public ResponseEntity<FarmerSearchResponse> search(
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "groupId", required = false) String groupId,
            @RequestParam(value = "status", required = false) FarmerStatus status,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(profileService.searchFarmers(phoneNumber, name, region, groupId, status, reason, page, size));
    }
}
