package com.umar.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DB-backed security toggle service allowing security enforcement for any microservice module
 * to be turned OFF or ON dynamically via database configuration without restarting.
 */
@Slf4j
@Service
public class SecurityToggleService {

    private final ObjectProvider<JdbcTemplate> jdbcProvider;

    @Value("${agreeshield.security.module-name:${spring.application.name:default-module}}")
    private String defaultModuleName;

    @Value("${agreeshield.security.default-enabled:true}")
    private boolean defaultEnabled;

    private volatile boolean schemaInitialized = false;

    public SecurityToggleService(ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.jdbcProvider = jdbcProvider;
    }

    @PostConstruct
    public void initSchemaIfAvailable() {
        ensureSchema();
    }

    public synchronized void ensureSchema() {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null || schemaInitialized) {
            return;
        }
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS security_module_config (
                        module_name VARCHAR(64) PRIMARY KEY,
                        security_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        allow_header_identity BOOLEAN NOT NULL DEFAULT TRUE,
                        allow_jwt BOOLEAN NOT NULL DEFAULT TRUE,
                        updated_by VARCHAR(64),
                        notes VARCHAR(255),
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """);
            seedModuleIfAbsent(jdbc, defaultModuleName);
            schemaInitialized = true;
        } catch (Exception ex) {
            log.warn("Could not initialize security_module_config table: {}", ex.getMessage());
        }
    }

    private void seedModuleIfAbsent(JdbcTemplate jdbc, String moduleName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM security_module_config WHERE module_name = ?",
                Integer.class, moduleName);
        if (count != null && count == 0) {
            jdbc.update(
                    "INSERT INTO security_module_config (module_name, security_enabled, allow_header_identity, allow_jwt, updated_by, notes, updated_at) VALUES (?, ?, TRUE, TRUE, 'system', 'Default module security policy', ?)",
                    moduleName, defaultEnabled, Timestamp.from(Instant.now()));
        }
    }

    public boolean isSecurityEnabled() {
        return isSecurityEnabled(defaultModuleName);
    }

    public boolean isSecurityEnabled(String moduleName) {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            return defaultEnabled;
        }
        ensureSchema();
        try {
            List<Boolean> rows = jdbc.query(
                    "SELECT security_enabled FROM security_module_config WHERE module_name = ?",
                    (rs, rowNum) -> rs.getBoolean("security_enabled"),
                    moduleName);
            if (rows.isEmpty()) {
                return defaultEnabled;
            }
            return Boolean.TRUE.equals(rows.getFirst());
        } catch (Exception ex) {
            log.debug("Falling back to defaultEnabled={} for {}: {}", defaultEnabled, moduleName, ex.getMessage());
            return defaultEnabled;
        }
    }

    public Map<String, Object> setModuleSecurity(String moduleName, boolean enabled, String updatedBy, String notes) {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("No JdbcTemplate available to persist security module configuration");
        }
        ensureSchema();
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.update(
                "UPDATE security_module_config SET security_enabled = ?, updated_by = ?, notes = ?, updated_at = ? WHERE module_name = ?",
                enabled, updatedBy != null ? updatedBy : "system", notes, now, moduleName);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO security_module_config (module_name, security_enabled, allow_header_identity, allow_jwt, updated_by, notes, updated_at) VALUES (?, ?, TRUE, TRUE, ?, ?, ?)",
                    moduleName, enabled, updatedBy != null ? updatedBy : "system", notes, now);
        }
        return getModuleConfig(moduleName);
    }

    public Map<String, Object> getModuleConfig(String moduleName) {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            return Map.of("moduleName", moduleName, "securityEnabled", defaultEnabled);
        }
        ensureSchema();
        seedModuleIfAbsent(jdbc, moduleName);
        return jdbc.queryForMap("SELECT module_name, security_enabled, allow_header_identity, allow_jwt, updated_by, notes FROM security_module_config WHERE module_name = ?", moduleName);
    }

    public List<Map<String, Object>> listModules() {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            return List.of();
        }
        ensureSchema();
        return jdbc.queryForList("SELECT module_name, security_enabled, allow_header_identity, allow_jwt, updated_by, notes FROM security_module_config ORDER BY module_name");
    }
}
