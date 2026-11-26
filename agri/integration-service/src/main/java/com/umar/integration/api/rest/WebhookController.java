package com.umar.integration.api.rest;

import com.umar.payload.request.integration.RegisterSubscriptionRequest;
import com.umar.payload.request.integration.ReplayDeliveriesRequest;
import com.umar.payload.request.integration.TestDeliveryRequest;
import com.umar.payload.request.integration.UpdateSubscriptionRequest;
import com.umar.integration.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    // INT-01: Register Webhook Subscription
    @PostMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> registerSubscription(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RegisterSubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(webhookService.registerSubscription(idempotencyKey, request));
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<Map<String, Object>>> listSubscriptions() {
        return ResponseEntity.ok(webhookService.listSubscriptions());
    }

    // INT-02: Get Subscription
    @GetMapping("/subscriptions/{id}")
    public ResponseEntity<Map<String, Object>> getSubscription(
            @PathVariable String id,
            @RequestParam(defaultValue = "health") String include,
            @RequestParam(defaultValue = "DAY") String window) {
        WebhookService.SubscriptionView view = webhookService.getSubscription(id, include, window);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + view.version() + "\"")
                .body(view.body());
    }

    // INT-02: Pause, Resume, or Rotate Key
    @PutMapping("/subscriptions/{id}")
    public ResponseEntity<Map<String, Object>> updateSubscription(
            @PathVariable String id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        WebhookService.SubscriptionView view = webhookService.updateSubscription(id, ifMatch, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + view.version() + "\"")
                .body(view.body());
    }

    // INT-02: Delete Subscription
    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<Void> deleteSubscription(
            @PathVariable String id,
            @RequestParam(required = false) Boolean confirm) {
        webhookService.deleteSubscription(id, confirm);
        return ResponseEntity.noContent().build();
    }

    // INT-04: Send Test Delivery
    @PostMapping("/subscriptions/{id}/test")
    public ResponseEntity<Map<String, Object>> sendTestDelivery(
            @PathVariable String id,
            @Valid @RequestBody TestDeliveryRequest request) {
        return ResponseEntity.ok(webhookService.sendTestDelivery(id, request));
    }

    // INT-05: Replay Deliveries
    @PostMapping("/deliveries/{id}/replay")
    public ResponseEntity<Map<String, Object>> replayDeliveries(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReplayDeliveriesRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(webhookService.replayDeliveries(id, idempotencyKey, request));
    }
}
