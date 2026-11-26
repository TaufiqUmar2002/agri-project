package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "notification-service",
        url = "${agreeshield.services.notification.url:http://localhost:8090}",
        contextId = "notificationServiceFeignClient"
)
public interface NotificationServiceFeignClient {
    @PostMapping("/api/notifications/messages")
    Map<String, Object> sendMessage(@RequestBody Map<String, Object> request);
}
