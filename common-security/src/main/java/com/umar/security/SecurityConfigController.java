package com.umar.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Allows administrators/ops to inspect or toggle DB-backed security enforcement per module at runtime.
 */
@RestController
@RequestMapping("/api/security/modules")
@RequiredArgsConstructor
public class SecurityConfigController {

    private final SecurityToggleService toggleService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        return ResponseEntity.ok(toggleService.listModules());
    }

    @GetMapping("/{moduleName}")
    public ResponseEntity<Map<String, Object>> getModule(@PathVariable String moduleName) {
        return ResponseEntity.ok(toggleService.getModuleConfig(moduleName));
    }

    @PutMapping("/{moduleName}")
    public ResponseEntity<Map<String, Object>> toggleModule(
            @PathVariable String moduleName,
            @RequestBody Map<String, Object> body) {
        boolean enabled = body.get("securityEnabled") == null || Boolean.parseBoolean(String.valueOf(body.get("securityEnabled")));
        String updatedBy = body.get("updatedBy") != null ? String.valueOf(body.get("updatedBy")) : "admin";
        String notes = body.get("notes") != null ? String.valueOf(body.get("notes")) : "Runtime DB toggle";
        return ResponseEntity.ok(toggleService.setModuleSecurity(moduleName, enabled, updatedBy, notes));
    }
}
