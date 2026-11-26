package com.umar.notification.controller;

import com.umar.notification.service.NotificationChannelsAndTemplatesService;
import com.umar.notification.service.NotificationDeliveryService;
import com.umar.payload.request.notification.*;
import com.umar.payload.response.notification.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationDeliveryService deliveryService;
    private final NotificationChannelsAndTemplatesService channelService;

    @PostMapping("/api/ussd/session")
    public ResponseEntity<Map<String, Object>> ussdSession(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @Valid @RequestBody UssdSessionRequest req) {
        return ResponseEntity.ok(channelService.handleUssdSession(signature, timestamp, req));
    }

    @GetMapping("/api/notifications/preferences")
    public ResponseEntity<NotificationPreferencesResponse> getPreferences(
            @RequestParam(value = "farmerId", required = false) String farmerId) {
        return ResponseEntity.ok(channelService.getPreferences(farmerId));
    }

    @PutMapping("/api/notifications/preferences")
    public ResponseEntity<NotificationPreferencesResponse> updatePreferences(
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody UpdateNotificationPreferencesRequest req) {
        NotificationPreferencesResponse resp = channelService.updatePreferences(ifMatch, req);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, "\"v" + resp.getVersion() + "\"").body(resp);
    }

    @PostMapping("/api/notifications/broadcast")
    public ResponseEntity<BroadcastResponse> broadcast(@RequestBody BroadcastAlertRequest req) {
        BroadcastResponse resp = channelService.handleBroadcast(req);
        HttpStatus status = switch (resp.getStatus()) {
            case "PENDING_APPROVAL" -> HttpStatus.ACCEPTED;
            case "COMPLETED", "SCHEDULED" -> "CREATE".equalsIgnoreCase(req.getAction()) || req.getAction() == null
                    ? HttpStatus.CREATED : HttpStatus.OK;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(resp);
    }

    @GetMapping("/api/notifications/{id}/status")
    public ResponseEntity<NotificationStatusResponse> getStatus(
            @PathVariable("id") String notificationId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        NotificationStatusResponse resp = deliveryService.getStatus(notificationId, ifNoneMatch);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, "\"v" + resp.getVersion() + "\"").body(resp);
    }

    @PostMapping("/api/notifications/messages")
    public ResponseEntity<SendDirectMessageResponse> sendDirectMessage(@Valid @RequestBody SendDirectMessageRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(deliveryService.sendDirectMessage(req));
    }

    @PostMapping("/api/notifications/callbacks/{aggregator}")
    public ResponseEntity<Map<String, Object>> deliveryCallback(
            @PathVariable("aggregator") String aggregator,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(deliveryService.processDeliveryCallback(aggregator, signature, timestamp, nonce, body));
    }

    @PostMapping("/api/notifications/inbound/{aggregator}")
    public ResponseEntity<Map<String, Object>> inboundMessage(
            @PathVariable("aggregator") String aggregator,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(channelService.processInboundMessage(aggregator, signature, timestamp, nonce, body));
    }

    @PutMapping("/api/notifications/templates/{code}/{language}")
    public ResponseEntity<TemplateResponse> manageTemplate(
            @PathVariable("code") String code,
            @PathVariable("language") String language,
            @Valid @RequestBody ManageTemplateRequest req) {
        return ResponseEntity.ok(channelService.manageTemplate(code, language, req));
    }

    @GetMapping("/api/notifications")
    public ResponseEntity<NotificationListResponse> listNotifications(
            @RequestParam(value = "farmerId", required = false) String farmerId,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(deliveryService.listNotifications(farmerId, status));
    }
}
