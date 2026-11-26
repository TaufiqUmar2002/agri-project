package com.umar.iot.controller;

import com.umar.enums.iot.ReadingResolution;
import com.umar.iot.service.IotDeviceService;
import com.umar.payload.request.iot.*;
import com.umar.payload.response.iot.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class IotController {

    private final IotDeviceService deviceService;

    @PostMapping
    public ResponseEntity<DeviceRegistrationResponse> register(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RegisterDeviceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.registerDevice(req, idempotencyKey));
    }

    @GetMapping
    public ResponseEntity<DeviceListResponse> list(
            @RequestParam(required = false) String connectivity,
            @RequestParam(required = false) String districtCode) {
        return ResponseEntity.ok(deviceService.listDevices(connectivity, districtCode));
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<DeviceHealthResponse> health(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        DeviceHealthResponse resp = deviceService.getHealth(id);
        String etag = "\"" + resp.getVersion() + "\"";
        if (etag.equals(ifNoneMatch)) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        return ResponseEntity.ok().eTag(etag).body(resp);
    }

    @PutMapping("/{id}/calibrate")
    public ResponseEntity<DeviceCalibrationResponse> calibrate(
            @PathVariable String id,
            @Valid @RequestBody CalibrateDeviceRequest req) {
        DeviceCalibrationResponse resp = deviceService.calibrateDevice(id, req);
        return "PENDING_APPROVAL".equals(resp.getStatus())
                ? ResponseEntity.accepted().body(resp)
                : ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/credentials")
    public ResponseEntity<DeviceCredentialResponse> credentials(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ManageDeviceCredentialsRequest req) {
        return ResponseEntity.ok(deviceService.manageCredentials(id, req, idempotencyKey));
    }

    @PutMapping("/{id}/binding")
    public ResponseEntity<DeviceBindingResponse> binding(
            @PathVariable String id,
            @Valid @RequestBody UpdateDeviceBindingRequest req) {
        DeviceBindingResponse resp = deviceService.updateBinding(id, req);
        return "PENDING_APPROVAL".equals(resp.getApprovalStatus())
                ? ResponseEntity.accepted().body(resp)
                : ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/readings")
    public ResponseEntity<DeviceReadingsResponse> readings(
            @PathVariable String id,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "DAILY") ReadingResolution resolution) {
        return ResponseEntity.ok(deviceService.getReadings(id, from, to, resolution));
    }
}
