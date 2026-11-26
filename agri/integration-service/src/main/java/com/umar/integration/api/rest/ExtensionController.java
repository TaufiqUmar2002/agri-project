package com.umar.integration.api.rest;

import com.umar.payload.request.integration.EnableExtensionRequest;
import com.umar.payload.request.integration.RegisterExtensionRequest;
import com.umar.payload.request.integration.RolloutExtensionRequest;
import com.umar.enums.integration.RolloutStage;
import com.umar.integration.service.CatalogService;
import com.umar.integration.service.ExtensionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/extensions")
@RequiredArgsConstructor
public class ExtensionController {

    private final ExtensionService extensionService;
    private final CatalogService catalogService;

    // INT-06: Register Extension
    @PostMapping
    public ResponseEntity<Map<String, Object>> registerExtension(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RegisterExtensionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(extensionService.registerExtension(idempotencyKey, request));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listExtensions() {
        return ResponseEntity.ok(extensionService.listExtensions());
    }

    // INT-07: Enable, Disable or Kill Extension
    @PutMapping("/{id}/enable")
    public ResponseEntity<Map<String, Object>> enableOrDisableOrKill(
            @PathVariable String id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody EnableExtensionRequest request) {
        ExtensionService.EnableResult result = extensionService.enableOrDisableOrKill(id, ifMatch, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + result.etagVersion() + "\"")
                .body(result.body());
    }

    // INT-11: Browse Extension Point Catalog
    @GetMapping("/points")
    public ResponseEntity<Map<String, Object>> browsePoints(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String type,
            @RequestParam(name = "protected", required = false) Boolean protectedFilter,
            @RequestParam(required = false) Boolean partnerVisible,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "limits") String include,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Map<String, Object> body = catalogService.browseCatalog(
                service, type, protectedFilter, partnerVisible, q, status, include, page, size);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + body.get("snapshotVersion") + "\"")
                .body(body);
    }

    // INT-12: Roll Out Extension (Shadow and Canary)
    @PostMapping("/{id}/rollout")
    public ResponseEntity<Map<String, Object>> rolloutExtension(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RolloutExtensionRequest request) {
        Map<String, Object> body = extensionService.rollout(id, idempotencyKey, request);
        HttpStatus status = (request.getStage() == RolloutStage.SHADOW || request.getStage() == RolloutStage.CANARY)
                ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    // Helper endpoint to create approval request for an extension version before INT-07 enable
    @PostMapping("/{id}/approvals")
    public ResponseEntity<Map<String, Object>> requestApproval(
            @PathVariable String id,
            @RequestParam(required = false) String version) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(extensionService.requestEnableApproval(id, version));
    }

    // REST bridge for microservices registering declared extension points via OpenFeign
    @PostMapping("/points/register")
    public ResponseEntity<Map<String, Object>> registerPointsRest(@RequestBody Map<String, Object> request) {
        String serviceName = String.valueOf(request.getOrDefault("serviceName", "unknown"));
        return ResponseEntity.ok(Map.of(
                "serviceName", serviceName,
                "status", "REGISTERED",
                "accepted", request.getOrDefault("points", List.of())
        ));
    }

    // REST bridge for microservices invoking extension hooks via OpenFeign
    @PostMapping("/invoke")
    public ResponseEntity<Map<String, Object>> invokeExtensionRest(@RequestBody Map<String, Object> request) {
        String hookId = String.valueOf(request.getOrDefault("hookId", ""));
        String caller = String.valueOf(request.getOrDefault("callerService", "unknown"));
        return ResponseEntity.ok(Map.of(
                "hookId", hookId,
                "callerService", caller,
                "status", "OK",
                "allowed", true
        ));
    }
}
