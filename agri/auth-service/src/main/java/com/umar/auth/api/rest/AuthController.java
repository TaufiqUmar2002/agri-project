package com.umar.auth.api.rest;

import com.umar.auth.service.AuthAccountService;
import com.umar.auth.service.DataRightsService;
import com.umar.auth.service.DelegationService;
import com.umar.payload.request.auth.*;
import com.umar.payload.response.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthAccountService authAccountService;
    private final DelegationService delegationService;
    private final DataRightsService dataRightsService;

    @PostMapping("/register")
    public ResponseEntity<RegisterAccountResponse> register(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest,
            @Valid @RequestBody RegisterAccountRequest request) {
        String ip = clientIp(servletRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(authAccountService.registerAccount(idempotencyKey, ip, request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(
            HttpServletRequest servletRequest,
            @Valid @RequestBody LoginRequest request) {
        AuthAccountService.LoginResult result = authAccountService.login(clientIp(servletRequest), request);
        return ResponseEntity.status(result.completed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(result.response());
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(
            HttpServletRequest servletRequest,
            @Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authAccountService.verifyOtp(clientIp(servletRequest), request));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(
            HttpServletRequest servletRequest,
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authAccountService.refreshToken(clientIp(servletRequest), request));
    }

    @PostMapping("/step-up")
    public ResponseEntity<RegisterAccountResponse> requestStepUp(
            @Valid @RequestBody StepUpRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(authAccountService.requestStepUpChallenge(request));
    }

    @PostMapping("/agents/{id}/enroll-on-behalf")
    public ResponseEntity<EnrollOnBehalfResponse> enrollOnBehalf(
            @PathVariable("id") UUID agentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody EnrollOnBehalfRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(delegationService.enrollOnBehalf(agentId, idempotencyKey, request));
    }

    @PostMapping("/data-requests")
    public ResponseEntity<DataRightsResponse> submitDataRequest(
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestHeader(value = "X-Step-Up-Token") String stepUpToken,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @Valid @RequestBody DataRightsRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(dataRightsService.submitDataRightsRequest(delegationHeader, stepUpToken, deviceId, request));
    }

    private String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "127.0.0.1";
    }
}
