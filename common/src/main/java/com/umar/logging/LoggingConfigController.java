package com.umar.logging;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logging/modules")
@RequiredArgsConstructor
public class LoggingConfigController {

    private final CentralLoggingService loggingService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        return ResponseEntity.ok(loggingService.listModules());
    }

    @GetMapping("/{moduleName}")
    public ResponseEntity<Map<String, Object>> getModule(@PathVariable String moduleName) {
        return ResponseEntity.ok(loggingService.getModuleConfig(moduleName));
    }

    @PutMapping("/{moduleName}")
    public ResponseEntity<Map<String, Object>> updateModule(
            @PathVariable String moduleName,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(loggingService.updateModuleConfig(moduleName, body));
    }
}
