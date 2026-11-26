package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "iot-service", url = "${agreeshield.services.iot.url:http://localhost:8091}", contextId = "iotServiceFeignClient")
public interface IotServiceFeignClient {
    @GetMapping("/api/devices/{deviceId}/health")
    Map<String, Object> getDeviceHealth(@PathVariable("deviceId") String deviceId);
}
